/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Mutable assembly context passed to each {@link ChainIdentityProvider#setup} during
 * credential chain construction.
 *
 * <p>Providers use this to:
 * <ul>
 *   <li>Read shared state ({@link #profile()}, {@link #profileFile()}, {@link #executor()})</li>
 *   <li>Write shared state ({@link #setProfileFile(AwsProfileFile)}, {@link #setProfile(AwsProfile)})</li>
 *   <li>Register resolvers ({@link #addResolver(IdentityResolver)}, {@link #addTerminalResolver(IdentityResolver)})</li>
 *   <li>Read environment variables ({@link #getenv(String)})</li>
 * </ul>
 *
 * <p>When {@link #addTerminalResolver(IdentityResolver)} is called, assembly stops immediately
 * and no further providers are invoked.
 *
 * <p>This class is not thread-safe. It is used only during the single-threaded assembly phase.
 * Resolvers MUST NOT retain a reference to this object.
 */
public final class ChainSetup {
    private final ScheduledExecutorService executor;
    private final String profileNameOverride;
    private final Context properties;
    private final List<NamedResolver> resolvers = new ArrayList<>();
    private final Function<String, String> envFn;
    private AwsProfileFile profileFile;
    private AwsProfile profile;
    private boolean terminal;
    private ChainIdentityProvider currentProvider;

    private ChainSetup(Builder builder) {
        this.executor = builder.executor;
        this.profileNameOverride = builder.profileNameOverride;
        this.properties = Context.create();
        this.envFn = builder.envFn;
        this.profileFile = builder.profileFile;
    }

    /**
     * Creates a new builder for {@link ChainSetup}.
     *
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the shared executor for scheduling background credential refresh tasks.
     *
     * @return the scheduled executor, or {@code null} if none was configured.
     */
    public ScheduledExecutorService executor() {
        return executor;
    }

    /**
     * Returns the client-specified profile name override, or {@code null} to use the
     * default resolution order ({@code AWS_PROFILE} env var, {@code aws.profile} system
     * property, then {@code "default"}).
     *
     * @return the profile name override, or {@code null}.
     */
    public String profileNameOverride() {
        return profileNameOverride;
    }

    /**
     * Returns the value of the given environment variable, or {@code null} if not set.
     *
     * <p>In production this delegates to {@link System#getenv(String)}. In tests, a custom
     * function can be provided via {@link Builder#env(Function)} for isolation.
     *
     * @param name the environment variable name.
     * @return the value, or {@code null}.
     */
    public String getenv(String name) {
        return envFn.apply(name);
    }

    /**
     * Returns a general-purpose typed property bag for sharing additional state between
     * providers during assembly.
     *
     * @return the shared context properties.
     */
    public Context properties() {
        return properties;
    }

    /**
     * Returns the parsed AWS config/credentials file, or {@code null} if not yet loaded.
     *
     * <p>Populated by the {@code SHARED_CONFIG} provider during assembly.
     *
     * @return the parsed profile file, or {@code null}.
     */
    public AwsProfileFile profileFile() {
        return profileFile;
    }

    /**
     * Sets the parsed AWS config/credentials file. Called by the {@code SHARED_CONFIG}
     * provider during assembly.
     *
     * @param profileFile the parsed profile file.
     */
    public void setProfileFile(AwsProfileFile profileFile) {
        this.profileFile = profileFile;
    }

    /**
     * Returns the active AWS profile, or {@code null} if not yet loaded.
     *
     * <p>Populated by the {@code SHARED_CONFIG} provider during assembly.
     *
     * @return the active profile, or {@code null}.
     */
    public AwsProfile profile() {
        return profile;
    }

    /**
     * Sets the active AWS profile. Called by the {@code SHARED_CONFIG} provider during assembly.
     *
     * @param profile the active profile.
     */
    public void setProfile(AwsProfile profile) {
        this.profile = profile;
    }

    /**
     * Registers a resolver at the current provider's position. Assembly continues after
     * this call. May be called multiple times to register multiple resolvers that stack
     * at this position.
     *
     * @param resolver the identity resolver to register.
     */
    public void addResolver(IdentityResolver<?> resolver) {
        resolvers.add(new NamedResolver(currentProvider.name(), currentProvider.featureIds(), resolver));
    }

    /**
     * Registers a resolver and stops assembly immediately. No further providers will be
     * called. Use when the credential source is authoritative once detected (e.g.,
     * environment variables contain a complete set of credentials, or a profile explicitly
     * configures assume-role).
     *
     * @param resolver the identity resolver to register.
     */
    public void addTerminalResolver(IdentityResolver<?> resolver) {
        resolvers.add(new NamedResolver(currentProvider.name(), currentProvider.featureIds(), resolver));
        this.terminal = true;
    }

    /**
     * Sets the current provider being assembled. Called by the chain before invoking
     * each provider's {@link ChainIdentityProvider#setup} method so that
     * {@link #addResolver} and {@link #addTerminalResolver} can associate the resolver
     * with the correct provider name and feature IDs.
     *
     * <p>This method is public to support unit testing of individual providers in
     * isolation. Production code should not call this directly.
     *
     * @param provider the provider currently being assembled.
     */
    @SmithyInternalApi
    public void setCurrentProvider(ChainIdentityProvider provider) {
        this.currentProvider = provider;
    }

    /**
     * Returns the list of resolvers registered during assembly, in the order they were added.
     *
     * @return the ordered list of named resolvers.
     */
    public List<NamedResolver> resolvers() {
        return resolvers;
    }

    boolean isTerminal() {
        return terminal;
    }

    /**
     * A resolver paired with the name and feature IDs of the provider that registered it.
     *
     * @param name       the canonical name of the provider that registered this resolver.
     * @param featureIds the feature IDs to emit on successful resolution.
     * @param resolver   the identity resolver.
     */
    public record NamedResolver(String name, Set<CredentialFeatureId> featureIds, IdentityResolver<?> resolver) {}

    /**
     * Builder for {@link ChainSetup}.
     */
    public static final class Builder {
        private ScheduledExecutorService executor;
        private String profileNameOverride;
        private Function<String, String> envFn = System::getenv;
        private AwsProfileFile profileFile;

        private Builder() {}

        /**
         * Sets the shared executor for background credential refresh.
         *
         * @param executor the scheduled executor.
         * @return this builder.
         */
        public Builder executor(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Sets the profile name override. When set, the {@code SHARED_CONFIG} provider
         * uses this name instead of resolving from {@code AWS_PROFILE} or system properties.
         *
         * @param profileNameOverride the profile name to use.
         * @return this builder.
         */
        public Builder profileNameOverride(String profileNameOverride) {
            this.profileNameOverride = profileNameOverride;
            return this;
        }

        /**
         * Sets the function used to resolve environment variables. Defaults to
         * {@link System#getenv(String)}. Override in tests for isolation.
         *
         * @param envFn the environment variable lookup function.
         * @return this builder.
         */
        public Builder env(Function<String, String> envFn) {
            this.envFn = envFn;
            return this;
        }

        /**
         * Supplies an already-parsed AWS config/credentials file. When set, the {@code SHARED_CONFIG} provider
         * uses this file instead of reading {@code ~/.aws/config} and {@code ~/.aws/credentials} from disk. Use
         * when the caller has already loaded the profile file, or to point the chain at a non-default location.
         *
         * @param profileFile the parsed profile file.
         * @return this builder.
         */
        public Builder profileFile(AwsProfileFile profileFile) {
            this.profileFile = profileFile;
            return this;
        }

        /**
         * Builds the {@link ChainSetup}.
         *
         * @return the constructed setup.
         */
        public ChainSetup build() {
            return new ChainSetup(this);
        }
    }
}
