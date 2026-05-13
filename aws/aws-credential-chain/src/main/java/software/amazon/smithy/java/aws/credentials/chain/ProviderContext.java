/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.concurrent.ScheduledExecutorService;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.context.Context;

/**
 * Shared context passed to each {@link ChainIdentityProvider#create} during chain assembly.
 *
 * <p>Providers earlier in the chain populate this context for downstream providers. In particular,
 * the {@code SHARED_CONFIG} provider parses the AWS config/credentials files and calls
 * {@link #setProfileFile} and {@link #setProfile} before any profile-based providers run.
 *
 * <p>This class is mutable. Providers may read and write to it during assembly; the context
 * is also accessible at request time by resolvers that close over it (e.g., for live reload
 * after {@code invalidate()}).
 */
public final class ProviderContext {
    private final ScheduledExecutorService executor;
    private final Context properties;
    private final String profileNameOverride;
    private AwsProfileFile profileFile;
    private AwsProfile profile;

    public ProviderContext(ScheduledExecutorService executor) {
        this(executor, null);
    }

    public ProviderContext(ScheduledExecutorService executor, String profileNameOverride) {
        this.executor = executor;
        this.properties = Context.create();
        this.profileNameOverride = profileNameOverride;
    }

    /**
     * Returns the shared executor for scheduling background credential refresh.
     *
     * @return the executor, or {@code null} if none was provided.
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
     * Returns a general-purpose typed property bag for sharing arbitrary state between providers.
     *
     * @return the property bag; never {@code null}.
     */
    public Context properties() {
        return properties;
    }

    /**
     * Returns the parsed AWS config/credentials file, or {@code null} if no config file was found.
     *
     * <p>Populated by the {@code SHARED_CONFIG} provider at assembly time. May be refreshed
     * in place after an {@code invalidate()} call.
     *
     * @return the profile file, or {@code null}.
     */
    public AwsProfileFile profileFile() {
        return profileFile;
    }

    /**
     * Sets the parsed profile file. Called by the {@code SHARED_CONFIG} provider during assembly
     * and potentially during invalidation to refresh from disk.
     *
     * @param profileFile the parsed profile file.
     */
    public void setProfileFile(AwsProfileFile profileFile) {
        this.profileFile = profileFile;
    }

    /**
     * Returns the active AWS profile (resolved from {@code AWS_PROFILE} env var,
     * {@code aws.profile} system property, or defaulting to {@code "default"}).
     *
     * <p>Returns {@code null} if no config file was loaded or the active profile name
     * does not exist in the file.
     *
     * @return the active profile, or {@code null}.
     */
    public AwsProfile profile() {
        return profile;
    }

    /**
     * Sets the active profile. Called by the {@code SHARED_CONFIG} provider during assembly
     * and potentially during invalidation.
     *
     * @param profile the active profile.
     */
    public void setProfile(AwsProfile profile) {
        this.profile = profile;
    }
}
