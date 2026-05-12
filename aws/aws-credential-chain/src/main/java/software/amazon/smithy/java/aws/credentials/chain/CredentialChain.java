/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A credential provider chain.
 *
 * <p>Discovers {@link ChainIdentityProvider} implementations via {@link ServiceLoader}, assembles them into an
 * ordered chain based on {@link StandardProvider} slots and relative ordering constraints, and resolves
 * credentials by trying each provider in order.
 *
 * <p>Usage:
 * <pre>{@code
 * var chain = CredentialChain.create();
 * var result = chain.resolveIdentity(Context.empty());
 * }</pre>
 *
 * <p>The chain is assembled once at creation time. Providers that are not on the classpath simply don't
 * participate: their slots are skipped. If no provider in the chain can resolve credentials, the chain returns an
 * error result describing which providers were tried.
 */
public final class CredentialChain<I extends Identity> implements IdentityResolver<I>, AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(CredentialChain.class);

    private final Class<I> identityType;
    private final List<NamedResolver<I>> resolvers;
    private final ScheduledExecutorService executor;

    private record NamedResolver<I extends Identity>(
            String name,
            Set<CredentialFeatureId> featureIds,
            IdentityResolver<I> resolver) {}

    private CredentialChain(
            Class<I> identityType,
            List<NamedResolver<I>> resolvers,
            ScheduledExecutorService executor
    ) {
        this.identityType = identityType;
        this.resolvers = resolvers;
        this.executor = executor;
    }

    /**
     * Create a credential chain by discovering providers via ServiceLoader.
     *
     * @param identityType Identity type to resolve.
     * @return the assembled chain.
     * @throws IllegalStateException if two providers claim the same standard slot.
     */
    public static <I extends Identity> CredentialChain<I> create(Class<I> identityType) {
        return create(identityType, Executors.newSingleThreadScheduledExecutor(r2 -> {
            Thread t = new Thread(r2, "aws-credential-chain-refresh");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * Create a credential chain by discovering providers via ServiceLoader.
     *
     * @param identityType Identity type to resolve.
     * @param ex Executor used for background resolution.
     * @return the assembled chain.
     * @throws IllegalStateException if two providers claim the same standard slot.
     */
    public static <I extends Identity> CredentialChain<I> create(Class<I> identityType, ScheduledExecutorService ex) {
        List<ChainIdentityProvider> registrations = new ArrayList<>();
        for (ChainIdentityProvider r : ServiceLoader.load(ChainIdentityProvider.class)) {
            registrations.add(r);
        }
        return assemble(identityType, registrations, ex);
    }

    static <I extends Identity> CredentialChain<I> assemble(
            Class<I> identityType,
            List<ChainIdentityProvider> registrations,
            ScheduledExecutorService executor
    ) {
        // Check for duplicate names.
        Set<String> seenNames = new HashSet<>();
        for (ChainIdentityProvider r : registrations) {
            if (!seenNames.add(r.name())) {
                throw new IllegalStateException("Duplicate credential provider registration name: '" + r.name() + "'");
            }
        }

        // Separate standards from relatives.
        Map<StandardProvider, ChainIdentityProvider> standards = new EnumMap<>(StandardProvider.class);
        List<ChainIdentityProvider> relatives = new ArrayList<>();

        for (ChainIdentityProvider r : registrations) {
            if (r.ordering() instanceof OrderingConstraint.Standard(StandardProvider slot)) {
                ChainIdentityProvider existing = standards.put(slot, r);
                if (existing != null) {
                    throw new IllegalStateException("Two credential providers claim the same slot '"
                            + slot + "': '" + existing.name() + "' and '" + r.name() + "'");
                }
            } else {
                relatives.add(r);
            }
        }

        // Use a single executor for each provider (used for caching).
        ProviderContext ctx = new ProviderContext(executor, Context.create());

        // Precompute insert positions: for each slot, how many claimed slots come before it
        // and up to and including it. This avoids re-scanning the enum on every relative insert.
        EnumMap<StandardProvider, Integer> insertAfter = new EnumMap<>(StandardProvider.class);
        EnumMap<StandardProvider, Integer> insertBefore = new EnumMap<>(StandardProvider.class);
        int count = 0;
        for (StandardProvider slot : StandardProvider.values()) {
            insertBefore.put(slot, count);
            if (standards.containsKey(slot)) {
                count++;
            }
            insertAfter.put(slot, count);
        }

        // Build the ordered list: standard slots in enum order.
        List<NamedResolver<I>> ordered = new ArrayList<>();
        for (StandardProvider slot : StandardProvider.values()) {
            ChainIdentityProvider r = standards.get(slot);
            if (r != null) {
                IdentityResolver<I> resolver = r.create(identityType, ctx);
                if (resolver != null) {
                    ordered.add(new NamedResolver<>(r.name(), r.featureIds(), resolver));
                }
            }
        }

        // Insert relative providers using precomputed positions.
        for (ChainIdentityProvider r : relatives) {
            int insertAt;
            if (r.ordering() instanceof OrderingConstraint.After(StandardProvider slot)) {
                insertAt = insertAfter.get(slot);
            } else if (r.ordering() instanceof OrderingConstraint.Before(StandardProvider slot)) {
                insertAt = insertBefore.get(slot);
            } else {
                insertAt = ordered.size();
            }
            if (insertAt > ordered.size()) {
                insertAt = ordered.size();
            }
            IdentityResolver<I> relResolver = r.create(identityType, ctx);
            if (relResolver != null) {
                ordered.add(insertAt, new NamedResolver<>(r.name(), r.featureIds(), relResolver));
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Assembled credential chain: {}",
                    ordered.stream().map(NamedResolver::name).collect(Collectors.joining(", ")));
        }

        warnDetectedButUnclaimed(standards);
        return new CredentialChain<>(identityType, Collections.unmodifiableList(ordered), executor);
    }

    private static void warnDetectedButUnclaimed(Map<StandardProvider, ?> standards) {
        for (StandardProvider slot : StandardProvider.values()) {
            if (slot.moduleSuggestion() != null && !standards.containsKey(slot) && slot.isDetected()) {
                LOGGER.warn("{} credentials detected but no provider is registered for the '{}' slot. "
                        + "Add '{}' to your dependencies.",
                        slot.name(),
                        slot.name(),
                        slot.moduleSuggestion());
            }
        }
    }

    @Override
    public IdentityResult<I> resolveIdentity(Context requestProperties) {
        if (resolvers.isEmpty()) {
            return IdentityResult.ofError(getClass(),
                    "No credential providers were discovered. Ensure at least one "
                            + "aws-credentials-* module is on the classpath." + detectedButMissingHints());
        }

        // More cheaply build up a list of failures, and defer string-ing them into a StringBuilder.
        List<Object> errors = new ArrayList<>();

        for (var nr : resolvers) {
            var result = nr.resolver.resolveIdentity(requestProperties);
            if (result.identity() != null) {
                if (!nr.featureIds.isEmpty()) {
                    var ids = requestProperties.get(CallContext.FEATURE_IDS);
                    if (ids != null) {
                        ids.addAll(nr.featureIds);
                    }
                }
                return result;
            }
            errors.add(nr.name);
            errors.add(result.error());
        }

        StringBuilder missing = new StringBuilder();
        for (var i = 0; i < errors.size(); i += 2) {
            if (i > 0) {
                errors.add("; ");
            }
            missing.append(errors.get(i)).append(": ").append(errors.get(i + 1));
        }

        return IdentityResult.ofError(getClass(),
                "Unable to resolve AWS credentials from any provider in the chain. Tried: " + missing
                        + detectedButMissingHints());
    }

    private String detectedButMissingHints() {
        StringBuilder hints = new StringBuilder();
        for (StandardProvider slot : StandardProvider.values()) {
            if (slot.moduleSuggestion() != null && slot.isDetected()) {
                if (!isClaimed(slot)) {
                    hints.append(" Detected ")
                            .append(slot.name())
                            .append(" credentials; add '")
                            .append(slot.moduleSuggestion())
                            .append("' to your dependencies.");
                }
            }
        }
        return hints.toString();
    }

    private boolean isClaimed(StandardProvider slot) {
        for (var nr : resolvers) {
            if (nr.name.equals(slot.name().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the ordered list of provider names in this chain.
     */
    public List<String> providerNames() {
        List<String> names = new ArrayList<>(resolvers.size());
        for (var nr : resolvers) {
            names.add(nr.name);
        }
        return names;
    }

    @Override
    public Class<I> identityType() {
        return identityType;
    }

    @Override
    public void invalidate() {
        for (var nr : resolvers) {
            nr.resolver.invalidate();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
