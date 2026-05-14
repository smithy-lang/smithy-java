/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.context.Context;

/**
 * Mutable assembly context passed to each {@link ChainIdentityProvider#create} during chain
 * construction. Providers use this to read shared state and register resolvers.
 *
 * <p>When {@link #addTerminalResolver} is called, assembly stops — no further providers are invoked.
 */
public final class ChainSetup {
    private final ScheduledExecutorService executor;
    private final String profileNameOverride;
    private final Context properties;
    private final List<NamedResolver> resolvers = new ArrayList<>();
    private AwsProfileFile profileFile;
    private AwsProfile profile;
    private boolean terminal;

    // Current provider being assembled (set by the chain before calling create())
    private ChainIdentityProvider currentProvider;

    public ChainSetup(ScheduledExecutorService executor) {
        this(executor, null);
    }

    public ChainSetup(ScheduledExecutorService executor, String profileNameOverride) {
        this.executor = executor;
        this.profileNameOverride = profileNameOverride;
        this.properties = Context.create();
    }

    /** Shared executor for background credential refresh. */
    public ScheduledExecutorService executor() {
        return executor;
    }

    /** Client-specified profile name override, or {@code null} for default resolution. */
    public String profileNameOverride() {
        return profileNameOverride;
    }

    /** General-purpose property bag for sharing state between providers. */
    public Context properties() {
        return properties;
    }

    /** The parsed AWS config/credentials file, or {@code null} if not yet loaded. */
    public AwsProfileFile profileFile() {
        return profileFile;
    }

    /** Sets the parsed profile file. Called by the SHARED_CONFIG provider. */
    public void setProfileFile(AwsProfileFile profileFile) {
        this.profileFile = profileFile;
    }

    /** The active profile, or {@code null} if not yet loaded. */
    public AwsProfile profile() {
        return profile;
    }

    /** Sets the active profile. Called by the SHARED_CONFIG provider. */
    public void setProfile(AwsProfile profile) {
        this.profile = profile;
    }

    /**
     * Registers a resolver at the current provider's position. Assembly continues.
     */
    public void addResolver(IdentityResolver<?> resolver) {
        resolvers.add(new NamedResolver(currentProvider.name(), currentProvider.featureIds(), resolver));
    }

    /**
     * Registers a resolver and stops assembly. No further providers will be called.
     */
    public void addTerminalResolver(IdentityResolver<?> resolver) {
        resolvers.add(new NamedResolver(currentProvider.name(), currentProvider.featureIds(), resolver));
        this.terminal = true;
    }

    // --- Package-private, used by CredentialChain ---

    public void setCurrentProvider(ChainIdentityProvider provider) {
        this.currentProvider = provider;
    }

    boolean isTerminal() {
        return terminal;
    }

    public List<NamedResolver> resolvers() {
        return resolvers;
    }

    public record NamedResolver<I extends Identity>(
            String name,
            Set<CredentialFeatureId> featureIds,
            IdentityResolver<I> resolver) {}
}
