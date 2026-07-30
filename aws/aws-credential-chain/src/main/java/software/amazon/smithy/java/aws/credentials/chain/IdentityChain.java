/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A chain of identity providers, parameterized by the {@link Identity} type it resolves (e.g.,
 * {@code AwsCredentialsIdentity} or {@code TokenIdentity}).
 *
 * <p>Discovers {@link ChainIdentityProvider} implementations via {@link ServiceLoader}, assembles them into an
 * ordered chain based on {@link StandardProvider} slots and relative ordering constraints, and resolves an
 * identity by trying each provider in order.
 *
 * <p>Usage:
 * {@snippet lang = "java":
 * var chain = IdentityChain.create();
 * var result = chain.resolveIdentity(Context.empty());
 *}
 *
 * <p>The chain is assembled once at creation time. Providers that are not on the classpath simply don't
 * participate: their slots are skipped. If no provider in the chain can resolve an identity, the chain returns an
 * error result describing which providers were tried.
 */
public final class IdentityChain<I extends Identity> implements IdentityResolver<I>, AutoCloseable {

    /**
     * Optional debugging hook. Register a {@link Consumer} of {@link ChainResolutionDiagnostics} under this key on the
     * request {@link Context} to observe the structured breakdown (providers tried, module suggestions) whenever
     * resolution fails. This keeps chain-specific diagnostics off the shared {@code IdentityResult} type. The same
     * information is always present in the human-readable {@code IdentityResult.error()} message.
     */
    public static final Context.Key<Consumer<ChainResolutionDiagnostics>> DIAGNOSTICS =
            Context.key("Credential chain resolution diagnostics sink");

    private static final InternalLogger LOGGER = InternalLogger.getLogger(IdentityChain.class);

    private final Class<I> identityType;
    private final List<ChainSetup.NamedResolver> resolvers;
    private final Set<StandardProvider> claimedSlots;
    private final Set<StandardProvider> detectedSlots;
    private final Function<String, String> envFn;
    private final ScheduledExecutorService executor;

    private IdentityChain(
            Class<I> identityType,
            List<ChainSetup.NamedResolver> resolvers,
            Set<StandardProvider> claimedSlots,
            Set<StandardProvider> detectedSlots,
            Function<String, String> envFn,
            ScheduledExecutorService executor
    ) {
        this.identityType = identityType;
        this.resolvers = resolvers;
        this.claimedSlots = claimedSlots;
        this.detectedSlots = detectedSlots;
        this.envFn = envFn;
        this.executor = executor;
    }

    /**
     * Create an identity chain by discovering providers via ServiceLoader.
     *
     * @param identityType Identity type to resolve.
     * @return the assembled chain.
     * @throws IllegalStateException if two providers claim the same standard slot.
     */
    public static <I extends Identity> IdentityChain<I> create(Class<I> identityType) {
        return create(identityType, defaultExecutor(), null, null);
    }

    /**
     * Create an identity chain by discovering providers via ServiceLoader, using a caller-supplied AWS
     * config/credentials file and region, with a default background-refresh executor.
     *
     * @param identityType Identity type to resolve.
     * @param profileFile Already-parsed profile file to use, or {@code null} to load from the default locations.
     * @param regionOverride Region for service-calling providers to use, or {@code null} to resolve it normally.
     * @return the assembled chain.
     * @throws IllegalStateException if two providers claim the same standard slot.
     */
    public static <I extends Identity> IdentityChain<I> create(
            Class<I> identityType,
            AwsProfileFile profileFile,
            String regionOverride
    ) {
        return create(identityType, defaultExecutor(), profileFile, regionOverride);
    }

    private static ScheduledExecutorService defaultExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r2 -> {
            Thread t = new Thread(r2, "aws-credential-chain-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Create an identity chain by discovering providers via ServiceLoader.
     *
     * @param identityType Identity type to resolve.
     * @param ex Executor used for background resolution. Ownership transfers to the returned chain. If assembly
     *           fails, the executor is shut down before the failure is propagated.
     * @return the assembled chain.
     * @throws IllegalStateException if two providers claim the same standard slot.
     */
    public static <I extends Identity> IdentityChain<I> create(Class<I> identityType, ScheduledExecutorService ex) {
        return create(identityType, ex, null, null);
    }

    /**
     * Create an identity chain by discovering providers via ServiceLoader, using a caller-supplied AWS
     * config/credentials file and region.
     *
     * <p>When {@code profileFile} is non-null, the {@code SHARED_CONFIG} provider uses it instead of reading
     * {@code ~/.aws/config} and {@code ~/.aws/credentials} from disk. Use this when the file has already been
     * loaded, or to point the chain at a non-default location.
     *
     * <p>When {@code regionOverride} is non-null, providers that resolve credentials via a service call (e.g.,
     * STS, SSO) use it for their endpoint instead of resolving the region from the environment or profile. This is
     * how a client's configured region flows into credential resolution.
     *
     * @param identityType Identity type to resolve.
     * @param ex Executor used for background resolution. Ownership transfers to the returned chain. If assembly
     *           fails, the executor is shut down before the failure is propagated.
     * @param profileFile Already-parsed profile file to use, or {@code null} to load from the default locations.
     * @param regionOverride Region for service-calling providers to use, or {@code null} to resolve it normally.
     * @return the assembled chain.
     * @throws IllegalStateException if two providers claim the same standard slot.
     */
    public static <I extends Identity> IdentityChain<I> create(
            Class<I> identityType,
            ScheduledExecutorService ex,
            AwsProfileFile profileFile,
            String regionOverride
    ) {
        List<ChainIdentityProvider> registrations;
        ChainSetup setup;
        try {
            registrations = new ArrayList<>();
            for (ChainIdentityProvider r : ServiceLoader.load(ChainIdentityProvider.class)) {
                registrations.add(r);
            }
            setup = ChainSetup.builder()
                    .executor(ex)
                    .profileFile(profileFile)
                    .regionOverride(regionOverride)
                    .build();
        } catch (RuntimeException | Error failure) {
            shutdownExecutor(ex, failure);
            throw failure;
        }
        return assemble(identityType, registrations, ex, setup);
    }

    static <I extends Identity> IdentityChain<I> assemble(
            Class<I> identityType,
            List<ChainIdentityProvider> registrations,
            ScheduledExecutorService executor
    ) {
        return assemble(identityType, registrations, executor, ChainSetup.builder().executor(executor).build());
    }

    /**
     * Assemble a chain using a caller-supplied {@link ChainSetup}. Lets tests inject a deterministic environment
     * and profile rather than reading the real process environment and config files.
     */
    static <I extends Identity> IdentityChain<I> assemble(
            Class<I> identityType,
            List<ChainIdentityProvider> registrations,
            ScheduledExecutorService executor,
            ChainSetup setup
    ) {
        try {
            // Check for duplicate names.
            Set<String> seenNames = new HashSet<>();
            for (ChainIdentityProvider r : registrations) {
                if (!seenNames.add(r.name())) {
                    throw new IllegalStateException(
                            "Duplicate credential provider registration name: '" + r.name() + "'");
                }
            }

            // Sort providers by ordering constraint (enum order for Standard, relative for Before/After).
            List<ChainIdentityProvider> sorted = sortByOrdering(registrations);

            // Call setup() on each provider in sorted order.
            for (ChainIdentityProvider provider : sorted) {
                setup.setCurrentProvider(provider);
                provider.setup(identityType, setup);
                if (setup.isTerminal()) {
                    break;
                }
            }

            var ordered = setup.resolvers();
            validateResolverTypes(identityType, ordered);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Assembled identity chain: {}",
                        ordered.stream().map(ChainSetup.NamedResolver::name).collect(Collectors.joining(", ")));
            }

            // A discovered registration claims its module's slot even when it does not produce a
            // resolver for this identity type or configuration.
            Set<StandardProvider> claimed = new HashSet<>();
            for (ChainIdentityProvider provider : sorted) {
                if (provider.ordering() instanceof OrderingConstraint.Standard(StandardProvider slot)) {
                    claimed.add(slot);
                }
            }
            Set<StandardProvider> detected = Set.copyOf(setup.detectedSlots());
            warnDetectedButUnclaimed(claimed, detected, setup.envFn());
            return new IdentityChain<>(identityType,
                    Collections.unmodifiableList(ordered),
                    Collections.unmodifiableSet(claimed),
                    detected,
                    setup.envFn(),
                    executor);
        } catch (RuntimeException | Error failure) {
            closeResolvers(setup.resolvers(), failure);
            shutdownExecutor(executor, failure);
            throw failure;
        }
    }

    private static void validateResolverTypes(
            Class<? extends Identity> identityType,
            List<ChainSetup.NamedResolver> resolvers
    ) {
        for (var namedResolver : resolvers) {
            Class<? extends Identity> resolverType = namedResolver.resolver().identityType();
            if (!identityType.isAssignableFrom(resolverType)) {
                throw new IllegalStateException(
                        "Credential provider '" + namedResolver.name() + "' registered a resolver for "
                                + resolverType.getName() + " when the chain requires " + identityType.getName());
            }
        }
    }

    private static List<ChainIdentityProvider> sortByOrdering(List<ChainIdentityProvider> providers) {
        ChainIdentityProvider[] standards = new ChainIdentityProvider[StandardProvider.values().length];
        for (ChainIdentityProvider p : providers) {
            if (p.ordering() instanceof OrderingConstraint.Standard(StandardProvider slot)) {
                if (standards[slot.ordinal()] != null) {
                    throw new IllegalStateException("Two providers claim the same standard slot '"
                            + slot + "': check provider '" + p.name() + "'");
                }
                standards[slot.ordinal()] = p;
            }
        }

        List<ChainIdentityProvider> result = new ArrayList<>(providers.size());
        for (StandardProvider slot : StandardProvider.values()) {
            addRelativeProviders(result, providers, slot, true);
            ChainIdentityProvider standard = standards[slot.ordinal()];
            if (standard != null) {
                result.add(standard);
            }
            addRelativeProviders(result, providers, slot, false);
        }
        return result;
    }

    private static void addRelativeProviders(
            List<ChainIdentityProvider> result,
            List<ChainIdentityProvider> providers,
            StandardProvider slot,
            boolean before
    ) {
        for (ChainIdentityProvider provider : providers) {
            if (before
                    && provider.ordering() instanceof OrderingConstraint.Before(StandardProvider target)
                    && target == slot) {
                result.add(provider);
            } else if (!before
                    && provider.ordering() instanceof OrderingConstraint.After(StandardProvider target)
                    && target == slot) {
                result.add(provider);
            }
        }
    }

    private static void warnDetectedButUnclaimed(
            Set<StandardProvider> claimed,
            Set<StandardProvider> detected,
            Function<String, String> envFn
    ) {
        for (StandardProvider slot : StandardProvider.values()) {
            if (slot.moduleSuggestion() != null
                    && !claimed.contains(slot)
                    && isDetected(slot, detected, envFn)) {
                LOGGER.warn("{} credentials detected but no provider is registered for the '{}' slot. "
                        + "Add '{}' to your dependencies.",
                        slot.name(),
                        slot.name(),
                        slot.moduleSuggestion());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public IdentityResult<I> resolveIdentity(Context requestProperties) {
        if (resolvers.isEmpty()) {
            emitDiagnostics(requestProperties, new ChainResolutionDiagnostics(List.of(), detectedButMissingModules()));
            return IdentityResult.ofError(getClass(),
                    "No credential providers were discovered. Ensure at least one "
                            + "aws-credentials-* module is on the classpath." + detectedButMissingHints());
        }

        // Track the ordered names of everything tried, and defer string-ing the errors into a message.
        List<String> providersTried = new ArrayList<>(resolvers.size());
        List<Object> errors = new ArrayList<>();

        for (var nr : resolvers) {
            var result = nr.resolver().resolveIdentity(requestProperties);
            if (result.identity() != null) {
                if (!nr.featureIds().isEmpty()) {
                    var ids = requestProperties.get(CallContext.FEATURE_IDS);
                    if (ids != null) {
                        ids.addAll(nr.featureIds());
                    }
                }
                return (IdentityResult<I>) result;
            }
            providersTried.add(nr.name());
            errors.add(nr.name());
            errors.add(result.error());
        }

        emitDiagnostics(requestProperties, new ChainResolutionDiagnostics(providersTried, detectedButMissingModules()));

        StringBuilder missing = new StringBuilder();
        for (var i = 0; i < errors.size(); i += 2) {
            if (i > 0) {
                missing.append("; ");
            }
            missing.append(errors.get(i)).append(": ").append(errors.get(i + 1));
        }

        return IdentityResult.ofError(getClass(),
                "Unable to resolve AWS credentials from any provider in the chain. Tried: " + missing
                        + detectedButMissingHints());
    }

    private void emitDiagnostics(Context requestProperties, ChainResolutionDiagnostics diagnostics) {
        var sink = requestProperties.get(DIAGNOSTICS);
        if (sink != null) {
            sink.accept(diagnostics);
        }
    }

    /**
     * @return the module-suggestion coordinates for every slot that is detected but has no registered provider,
     *         in slot order. Never null; empty when there is nothing to suggest.
     */
    private List<String> detectedButMissingModules() {
        List<String> suggestions = new ArrayList<>();
        for (StandardProvider slot : StandardProvider.values()) {
            if (slot.moduleSuggestion() != null && isDetected(slot) && !isClaimed(slot)) {
                suggestions.add(slot.moduleSuggestion());
            }
        }
        return suggestions;
    }

    private String detectedButMissingHints() {
        StringBuilder hints = new StringBuilder();
        for (StandardProvider slot : StandardProvider.values()) {
            if (slot.moduleSuggestion() != null && isDetected(slot) && !isClaimed(slot)) {
                hints.append(" Detected ")
                        .append(slot.name())
                        .append(" credentials; add '")
                        .append(slot.moduleSuggestion())
                        .append("' to your dependencies.");
            }
        }
        return hints.toString();
    }

    private boolean isClaimed(StandardProvider slot) {
        return claimedSlots.contains(slot);
    }

    private boolean isDetected(StandardProvider slot) {
        return isDetected(slot, detectedSlots, envFn);
    }

    private static boolean isDetected(
            StandardProvider slot,
            Set<StandardProvider> detectedSlots,
            Function<String, String> envFn
    ) {
        return detectedSlots.contains(slot) || slot.isDetected(envFn);
    }

    /**
     * @return the ordered list of provider names in this chain.
     */
    public List<String> providerNames() {
        List<String> names = new ArrayList<>(resolvers.size());
        for (var nr : resolvers) {
            names.add(nr.name());
        }
        return names;
    }

    @Override
    public Class<I> identityType() {
        return identityType;
    }

    @Override
    public void invalidate(I rejectedIdentity) {
        for (var nr : resolvers) {
            invalidateResolver(nr.resolver(), rejectedIdentity);
        }
    }

    private static <R extends Identity> void invalidateResolver(
            IdentityResolver<R> resolver,
            Identity rejectedIdentity
    ) {
        if (resolver.identityType().isInstance(rejectedIdentity)) {
            resolver.invalidate(resolver.identityType().cast(rejectedIdentity));
        }
    }

    @Override
    public void close() {
        closeResolvers(resolvers, null);
        shutdownExecutor(executor, null);
    }

    private static void closeResolvers(List<ChainSetup.NamedResolver> resolvers, Throwable priorFailure) {
        Set<IdentityResolver<?>> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var namedResolver : resolvers) {
            IdentityResolver<?> resolver = namedResolver.resolver();
            if (closed.add(resolver) && resolver instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception error) {
                    if (priorFailure == null) {
                        LOGGER.warn("Failed to close credential resolver {}: {}", namedResolver.name(), error);
                    } else {
                        priorFailure.addSuppressed(error);
                    }
                } catch (Error error) {
                    if (priorFailure == null) {
                        throw error;
                    }
                    priorFailure.addSuppressed(error);
                }
            }
        }
    }

    private static void shutdownExecutor(ScheduledExecutorService executor, Throwable priorFailure) {
        if (executor != null) {
            try {
                executor.shutdownNow();
            } catch (RuntimeException | Error error) {
                if (priorFailure == null) {
                    throw error;
                }
                priorFailure.addSuppressed(error);
            }
        }
    }
}
