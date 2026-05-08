/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
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
        // Index by name and aliases.
        Map<String, AwsCredentialProvider> byName = new HashMap<>();
        for (AwsCredentialProvider r : registrations) {
            if (byName.put(r.name(), r) != null) {
                throw new IllegalStateException("Duplicate credential provider registration name: '" + r.name() + "'");
            }
            for (String alias : r.aliases()) {
                byName.put(alias, r);
            }
        }

        // Separate builtins from relatives.
        Map<BuiltinProvider, AwsCredentialProvider> builtins = new LinkedHashMap<>();
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
        ProviderContext ctx = new ProviderContext(executor);

        // Build the ordered list: builtin slots first (in enum order), then insert relatives.
        List<NamedResolver> ordered = new ArrayList<>();
        List<String> orderedNames = new ArrayList<>();

        for (BuiltinProvider slot : BuiltinProvider.values()) {
            AwsCredentialProvider r = builtins.get(slot);
            if (r != null) {
                ordered.add(new NamedResolver(r.name(), r.create(ctx)));
                orderedNames.add(r.name());
            }
        }

        // Insert relative providers.
        for (AwsCredentialProvider r : relatives) {
            String target;
            int insertAt;
            if (r.ordering() instanceof OrderingConstraint.After(String provider)) {
                target = resolveName(provider, byName);
                int idx = orderedNames.indexOf(target);
                if (idx < 0) {
                    throw new IllegalStateException("Credential provider '" + r.name() + "' references unknown "
                            + "provider '" + provider + "' in its 'after' constraint.");
                }
                insertAt = idx + 1;
            } else if (r.ordering() instanceof OrderingConstraint.Before(String provider)) {
                target = resolveName(provider, byName);
                int idx = orderedNames.indexOf(target);
                if (idx < 0) {
                    throw new IllegalStateException("Credential provider '" + r.name() + "' references unknown "
                            + "provider '" + provider + "' in its 'before' constraint.");
                }
                insertAt = idx;
            } else {
                insertAt = ordered.size();
            }

            ordered.add(insertAt, new NamedResolver(r.name(), r.create(ctx)));
            orderedNames.add(insertAt, r.name());
        }

        LOGGER.debug("Assembled credential chain: {}", orderedNames);
        warnDetectedButUnclaimed(builtins);
        return new AwsCredentialChain(Collections.unmodifiableList(ordered), executor);
    }

    private static void warnDetectedButUnclaimed(Map<BuiltinProvider, AwsCredentialProvider> builtins) {
        for (BuiltinProvider slot : BuiltinProvider.values()) {
            if (!builtins.containsKey(slot) && slot.moduleSuggestion() != null && slot.isDetected()) {
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

    private static String resolveName(String target, Map<String, AwsCredentialProvider> byName) {
        AwsCredentialProvider resolved = byName.get(target);
        return resolved != null ? resolved.name() : target;
    }

    private record NamedResolver(String name, IdentityResolver<AwsCredentialsIdentity> resolver) {}
}
