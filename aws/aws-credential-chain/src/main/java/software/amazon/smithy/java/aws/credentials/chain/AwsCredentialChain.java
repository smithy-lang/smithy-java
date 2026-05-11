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
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * The AWS default credential provider chain.
 *
 * <p>Discovers {@link AwsCredentialProvider} implementations via {@link ServiceLoader}, assembles them into an
 * ordered chain based on {@link BuiltinProvider} slots and relative ordering constraints, and resolves
 * credentials by trying each provider in order.
 *
 * <p>Usage:
 * <pre>{@code
 * AwsCredentialsResolver chain = AwsCredentialChain.create();
 * IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());
 * }</pre>
 *
 * <p>The chain is assembled once at creation time. Providers that are not on the classpath simply don't
 * participate: their slots are skipped. If no provider in the chain can resolve credentials, the chain returns an
 * error result describing which providers were tried.
 */
public final class AwsCredentialChain implements AwsCredentialsResolver, AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(AwsCredentialChain.class);

    private final List<NamedResolver> resolvers;
    private final ScheduledExecutorService executor;

    private AwsCredentialChain(List<NamedResolver> resolvers, ScheduledExecutorService executor) {
        this.resolvers = resolvers;
        this.executor = executor;
    }

    /**
     * Create a credential chain by discovering providers via ServiceLoader.
     *
     * @return the assembled chain.
     * @throws IllegalStateException if two providers claim the same builtin slot.
     */
    public static AwsCredentialChain create() {
        List<AwsCredentialProvider> registrations = new ArrayList<>();
        for (AwsCredentialProvider r : ServiceLoader.load(AwsCredentialProvider.class)) {
            registrations.add(r);
        }
        return assemble(registrations);
    }

    static AwsCredentialChain assemble(List<AwsCredentialProvider> registrations) {
        // Check for duplicate names.
        Set<String> seenNames = new HashSet<>();
        for (AwsCredentialProvider r : registrations) {
            if (!seenNames.add(r.name())) {
                throw new IllegalStateException("Duplicate credential provider registration name: '" + r.name() + "'");
            }
        }

        // Separate builtins from relatives.
        Map<BuiltinProvider, AwsCredentialProvider> builtins = new EnumMap<>(BuiltinProvider.class);
        List<AwsCredentialProvider> relatives = new ArrayList<>();

        for (AwsCredentialProvider r : registrations) {
            if (r.ordering() instanceof OrderingConstraint.Builtin(BuiltinProvider slot)) {
                AwsCredentialProvider existing = builtins.put(slot, r);
                if (existing != null) {
                    throw new IllegalStateException("Two credential providers claim the same slot '"
                            + slot + "': '" + existing.name() + "' and '" + r.name() + "'");
                }
            } else {
                relatives.add(r);
            }
        }

        // Use a single executor for each provider (used for caching).
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r2 -> {
            Thread t = new Thread(r2, "aws-credential-chain-refresh");
            t.setDaemon(true);
            return t;
        });
        ProviderContext ctx = new ProviderContext(executor, Context.create());

        // Precompute insert positions: for each slot, how many claimed slots come before it
        // and up to and including it. This avoids re-scanning the enum on every relative insert.
        EnumMap<BuiltinProvider, Integer> insertAfter = new EnumMap<>(BuiltinProvider.class);
        EnumMap<BuiltinProvider, Integer> insertBefore = new EnumMap<>(BuiltinProvider.class);
        int count = 0;
        for (BuiltinProvider slot : BuiltinProvider.values()) {
            insertBefore.put(slot, count);
            if (builtins.containsKey(slot)) {
                count++;
            }
            insertAfter.put(slot, count);
        }

        // Build the ordered list: builtin slots in enum order.
        List<NamedResolver> ordered = new ArrayList<>();
        for (BuiltinProvider slot : BuiltinProvider.values()) {
            AwsCredentialProvider r = builtins.get(slot);
            if (r != null) {
                ordered.add(new NamedResolver(r.name(), r.featureIds(), r.create(ctx)));
            }
        }

        // Insert relative providers using precomputed positions.
        for (AwsCredentialProvider r : relatives) {
            int insertAt;
            if (r.ordering() instanceof OrderingConstraint.After(BuiltinProvider slot)) {
                insertAt = insertAfter.get(slot);
            } else if (r.ordering() instanceof OrderingConstraint.Before(BuiltinProvider slot)) {
                insertAt = insertBefore.get(slot);
            } else {
                insertAt = ordered.size();
            }
            if (insertAt > ordered.size()) {
                insertAt = ordered.size();
            }
            ordered.add(insertAt, new NamedResolver(r.name(), r.featureIds(), r.create(ctx)));
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Assembled credential chain: {}",
                    ordered.stream().map(NamedResolver::name).collect(Collectors.joining(", ")));
        }

        warnDetectedButUnclaimed(builtins);
        return new AwsCredentialChain(Collections.unmodifiableList(ordered), executor);
    }

    private static void warnDetectedButUnclaimed(Map<BuiltinProvider, AwsCredentialProvider> builtins) {
        for (BuiltinProvider slot : BuiltinProvider.values()) {
            if (slot.moduleSuggestion() != null && !builtins.containsKey(slot) && slot.isDetected()) {
                LOGGER.warn("{} credentials detected but no provider is registered for the '{}' slot. "
                        + "Add '{}' to your dependencies.",
                        slot.name(),
                        slot.name(),
                        slot.moduleSuggestion());
            }
        }
    }

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
        if (resolvers.isEmpty()) {
            return IdentityResult.ofError(getClass(),
                    "No credential providers were discovered. Ensure at least one "
                            + "aws-credentials-* module is on the classpath." + detectedButMissingHints());
        }

        // More cheaply build up a list of failures, and defer string-ing them into a StringBuilder.
        List<Object> errors = new ArrayList<>();

        for (NamedResolver nr : resolvers) {
            IdentityResult<AwsCredentialsIdentity> result = nr.resolver.resolveIdentity(requestProperties);
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
        for (BuiltinProvider slot : BuiltinProvider.values()) {
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

    private boolean isClaimed(BuiltinProvider slot) {
        for (NamedResolver nr : resolvers) {
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
        for (NamedResolver nr : resolvers) {
            names.add(nr.name);
        }
        return names;
    }

    @Override
    public void invalidate() {
        for (NamedResolver nr : resolvers) {
            nr.resolver.invalidate();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record NamedResolver(
            String name,
            Set<CredentialFeatureId> featureIds,
            IdentityResolver<AwsCredentialsIdentity> resolver) {}
}
