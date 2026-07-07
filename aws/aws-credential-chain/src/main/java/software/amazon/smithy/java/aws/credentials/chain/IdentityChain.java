/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
    private final Function<String, String> envFn;
    private final ScheduledExecutorService executor;

    private IdentityChain(
            Class<I> identityType,
            List<ChainSetup.NamedResolver> resolvers,
            Set<StandardProvider> claimedSlots,
            Function<String, String> envFn,
            ScheduledExecutorService executor
    ) {
        this.identityType = identityType;
        this.resolvers = resolvers;
        this.claimedSlots = claimedSlots;
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
     * @param ex Executor used for background resolution.
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
     * @param ex Executor used for background resolution.
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
        List<ChainIdentityProvider> registrations = new ArrayList<>();
        for (ChainIdentityProvider r : ServiceLoader.load(ChainIdentityProvider.class)) {
            registrations.add(r);
        }
        ChainSetup setup = ChainSetup.builder()
                .executor(ex)
                .profileFile(profileFile)
                .regionOverride(regionOverride)
                .build();
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
        // Check for duplicate names.
        Set<String> seenNames = new HashSet<>();
        for (ChainIdentityProvider r : registrations) {
            if (!seenNames.add(r.name())) {
                throw new IllegalStateException("Duplicate credential provider registration name: '" + r.name() + "'");
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

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Assembled identity chain: {}",
                    ordered.stream().map(ChainSetup.NamedResolver::name).collect(Collectors.joining(", ")));
        }

        // Warn about detected-but-unclaimed slots.
        Set<StandardProvider> claimed = new HashSet<>();
        for (var nr : ordered) {
            for (ChainIdentityProvider p : sorted) {
                if (p.name().equals(nr.name())
                        && p.ordering() instanceof OrderingConstraint.Standard(StandardProvider s)) {
                    claimed.add(s);
                }
            }
        }
        warnDetectedButUnclaimed(claimed, setup.envFn());
        return new IdentityChain<>(identityType,
                Collections.unmodifiableList(ordered),
                Collections.unmodifiableSet(claimed),
                setup.envFn(),
                executor);
    }

    private static List<ChainIdentityProvider> sortByOrdering(List<ChainIdentityProvider> providers) {
        // Separate into standard-slot providers and relative providers.
        List<ChainIdentityProvider> standards = new ArrayList<>();
        List<ChainIdentityProvider> befores = new ArrayList<>();
        List<ChainIdentityProvider> afters = new ArrayList<>();
        Set<StandardProvider> seenSlots = new HashSet<>();

        for (ChainIdentityProvider p : providers) {
            switch (p.ordering()) {
                case OrderingConstraint.Standard(StandardProvider slot) -> {
                    if (!seenSlots.add(slot)) {
                        throw new IllegalStateException("Two providers claim the same standard slot '"
                                + slot + "': check provider '" + p.name() + "'");
                    }
                    standards.add(p);
                }
                case OrderingConstraint.Before b -> befores.add(p);
                case OrderingConstraint.After a -> afters.add(p);
            }
        }

        // Sort standards by enum ordinal.
        standards.sort((a, b) -> {
            var slotA = ((OrderingConstraint.Standard) a.ordering()).slot();
            var slotB = ((OrderingConstraint.Standard) b.ordering()).slot();
            return slotA.compareTo(slotB);
        });

        // Build final list: insert Before/After relative to their referenced slot's position.
        List<ChainIdentityProvider> result = new ArrayList<>(standards);
        for (ChainIdentityProvider p : befores) {
            var slot = ((OrderingConstraint.Before) p.ordering()).slot();
            int idx = indexOfSlot(result, slot);
            result.add(idx, p);
        }
        for (ChainIdentityProvider p : afters) {
            var slot = ((OrderingConstraint.After) p.ordering()).slot();
            int idx = indexOfSlot(result, slot);
            int insertAt = Math.min(idx + 1, result.size());
            result.add(insertAt, p);
        }
        return result;
    }

    private static int indexOfSlot(List<ChainIdentityProvider> list, StandardProvider slot) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).ordering() instanceof OrderingConstraint.Standard(StandardProvider s) && s == slot) {
                return i;
            }
            // If slot not found, find where it would be by enum order.
            if (list.get(i).ordering() instanceof OrderingConstraint.Standard(StandardProvider s)
                    && s.ordinal() > slot.ordinal()) {
                return i;
            }
        }
        return list.size();
    }

    private static void warnDetectedButUnclaimed(Set<StandardProvider> claimed, Function<String, String> envFn) {
        for (StandardProvider slot : StandardProvider.values()) {
            if (slot.moduleSuggestion() != null && !claimed.contains(slot) && slot.isDetected(envFn)) {
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
            if (slot.moduleSuggestion() != null && slot.isDetected(envFn) && !isClaimed(slot)) {
                suggestions.add(slot.moduleSuggestion());
            }
        }
        return suggestions;
    }

    private String detectedButMissingHints() {
        StringBuilder hints = new StringBuilder();
        for (StandardProvider slot : StandardProvider.values()) {
            if (slot.moduleSuggestion() != null && slot.isDetected(envFn) && !isClaimed(slot)) {
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
    public void invalidate() {
        for (var nr : resolvers) {
            nr.resolver().invalidate();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
