/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSourceHandler.ResolutionContext;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.context.Context;

/**
 * An {@link AwsCredentialsResolver} that reads credentials from a profile in the AWS shared
 * configuration / credentials files by dispatching to a chain of
 * {@link AwsConfigCredentialSourceHandler}s.
 *
 * <h2>Architecture</h2>
 *
 * <p>Responsibilities are split so that the data model and credential-acquisition policy stay
 * independent:
 *
 * <ul>
 *   <li>{@link AwsProfileFile} / {@link AwsProfile} own the loaded profile data. A profile exposes
 *       an ordered list of {@link AwsConfigCredentialSource credential sources} computed from its
 *       properties, in AWS SDK shared-configuration priority order (all sources a profile
 *       declares are returned, not only the SEP "winner").</li>
 *   <li>{@link AwsConfigCredentialSourceHandler}s provide the strategies for turning a given source type
 *       into an identity. They are plugged in at construction time and may come from other
 *       modules (for example, an STS-backed handler for {@link AwsConfigCredentialSource.AssumeRole}).</li>
 *   <li>This class walks the profile's source list in priority order. For each source, it tries
 *       handlers in the order they were registered; the first handler whose {@code tryResolve}
 *       returns non-null wins. Sources whose types no handler claims are skipped and the next
 *       source is attempted. If no source is claimed by any handler, an
 *       {@link IdentityResult#ofError(Class, String) error result} is returned so this resolver
 *       can itself be composed in a wider resolver chain.</li>
 * </ul>
 *
 * <p>The module ships with handlers for {@link AwsConfigCredentialSource.StaticKeys} and
 * {@link AwsConfigCredentialSource.SessionKeys}. A builder that has no handlers registered at
 * {@link Builder#build()} time defaults to those two, so the out-of-the-box resolver behaves
 * the same as a hand-rolled "basic + session" static credentials resolver while leaving role /
 * SSO / process support pluggable.
 *
 * <h2>Profile name selection</h2>
 *
 * <ol>
 *   <li>Builder's {@code profileName}, if set.</li>
 *   <li>The {@code AWS_PROFILE} environment variable, if set and non-empty.</li>
 *   <li>The {@code AWS_DEFAULT_PROFILE} environment variable, if set and non-empty.</li>
 *   <li>The literal {@code "default"}.</li>
 * </ol>
 *
 * <p>{@link #refresh()} mutates the underlying {@link AwsProfileFile} in place (via
 * {@link AwsProfileFile#refresh()}). Concurrent callers of {@link #resolveIdentity(Context)}
 * observe the new state atomically after refresh completes.
 */
public final class AwsProfileCredentialsResolver implements AwsCredentialsResolver {

    /** Environment variable used to select the default profile name. */
    public static final String AWS_PROFILE_ENV = "AWS_PROFILE";

    /** Legacy environment variable used to select the default profile name. */
    public static final String AWS_DEFAULT_PROFILE_ENV = "AWS_DEFAULT_PROFILE";

    /** Profile name used when nothing else is configured. */
    public static final String DEFAULT_PROFILE_NAME = "default";

    private final String profileName;
    private final List<AwsConfigCredentialSourceHandler> handlers;
    private final boolean ignoreUnhandledSources;
    private final AwsProfileFile profileFile;
    private final String sourceDescription;
    private final IdentityResult<AwsCredentialsIdentity> profileNotFoundError;

    private AwsProfileCredentialsResolver(Builder b) {
        this.profileName = b.profileName != null ? b.profileName : resolveDefaultProfileName();
        this.handlers = b.handlers.isEmpty() ? discoverHandlers() : List.copyOf(b.handlers);
        this.ignoreUnhandledSources = b.ignoreUnhandledSources;

        if (b.profileFile != null) {
            this.profileFile = b.profileFile;
        } else {
            AwsProfileFile.Builder fileBuilder = AwsProfileFile.builder();
            if (b.configFileSet) {
                fileBuilder.configFile(b.configFile);
            }
            if (b.credentialsFileSet) {
                fileBuilder.credentialsFile(b.credentialsFile);
            }
            this.profileFile = fileBuilder.build();
        }

        sourceDescription = describeSource(profileFile);
        // Cached here since it could be returned over and over.
        profileNotFoundError = IdentityResult.ofError(
                getClass(),
                "AWS profile '" + profileName + "' was not found in " + sourceDescription);
    }

    private static List<AwsConfigCredentialSourceHandler> discoverHandlers() {
        List<AwsConfigCredentialSourceHandler> found = new ArrayList<>();
        for (AwsConfigCredentialSourceHandler h : ServiceLoader.load(AwsConfigCredentialSourceHandler.class)) {
            found.add(h);
        }
        return Collections.unmodifiableList(found);
    }

    private static String describeSource(AwsProfileFile file) {
        Path config = file.configFile();
        Path credentials = file.credentialsFile();
        if (config == null && credentials == null) {
            return "the configured AWS profile file";
        }

        StringBuilder sb = new StringBuilder();
        if (config != null) {
            sb.append(config);
        }
        if (credentials != null) {
            if (!sb.isEmpty()) {
                sb.append(" or ");
            }
            sb.append(credentials);
        }
        return sb.toString();
    }

    /**
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the profile name this resolver looks up.
     */
    public String profileName() {
        return profileName;
    }

    /**
     * @return the {@link AwsProfileFile} snapshot used by this resolver. The instance is live;
     *         calling {@link AwsProfileFile#refresh()} on it reloads from disk.
     */
    public AwsProfileFile profileFile() {
        return profileFile;
    }

    /**
     * @return an unmodifiable, ordered view of this resolver's registered handlers.
     */
    public List<AwsConfigCredentialSourceHandler> handlers() {
        return handlers;
    }

    /**
     * Re-read the underlying {@link AwsProfileFile} from disk. Delegates to {@link AwsProfileFile#refresh()},
     * which mutates the file in place.
     */
    public void refresh() {
        profileFile.refresh();
    }

    @Override
    public void invalidate() {
        profileFile.refresh();
    }

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
        // Access each time since it can be refreshed.
        AwsProfile profile = profileFile.profile(profileName);
        if (profile == null) {
            return profileNotFoundError;
        }

        List<AwsConfigCredentialSource> sources = profile.credentialSources();
        if (sources.isEmpty()) {
            return IdentityResult.ofError(
                    getClass(),
                    "AWS profile '" + profileName + "' in " + sourceDescription
                            + " does not describe any credential source.");
        }

        ResolutionContext ctx = new ResolutionContext(profileFile, profileName, requestProperties);
        for (AwsConfigCredentialSource source : sources) {
            IdentityResult<AwsCredentialsIdentity> result = tryHandlers(source, ctx);
            if (result != null) {
                return result;
            } else if (!ignoreUnhandledSources) {
                break;
            }
        }

        String typeName = sources.get(0).getClass().getSimpleName();
        return IdentityResult.ofError(
                getClass(),
                "AWS profile '" + profileName + "' requires a credential source of type '" + typeName + "', "
                        + "but no handler in this resolver claims it. Add an appropriate AwsConfigCredentialSourceHandler "
                        + "(for example, an STS or SSO-backed handler from another module).");
    }

    private IdentityResult<AwsCredentialsIdentity> tryHandlers(
            AwsConfigCredentialSource source,
            ResolutionContext ctx
    ) {
        for (AwsConfigCredentialSourceHandler handler : handlers) {
            IdentityResult<AwsCredentialsIdentity> attempt = handler.tryResolve(source, ctx);
            if (attempt != null) {
                if (!handler.featureIds().isEmpty()) {
                    var ids = ctx.requestProperties().get(CallContext.FEATURE_IDS);
                    if (ids != null) {
                        ids.addAll(handler.featureIds());
                    }
                }
                return attempt;
            }
        }
        return null;
    }

    private static String resolveDefaultProfileName() {
        String name = System.getenv(AWS_PROFILE_ENV);
        if (name != null && !name.isEmpty()) {
            return name;
        }

        name = System.getenv(AWS_DEFAULT_PROFILE_ENV);
        if (name != null && !name.isEmpty()) {
            return name;
        }

        return DEFAULT_PROFILE_NAME;
    }

    /**
     * Builder for {@link AwsProfileCredentialsResolver}.
     */
    public static final class Builder {
        private String profileName;
        private AwsProfileFile profileFile;
        private Path configFile;
        private boolean configFileSet;
        private Path credentialsFile;
        private boolean credentialsFileSet;
        private final List<AwsConfigCredentialSourceHandler> handlers = new ArrayList<>();
        private boolean ignoreUnhandledSources;

        private Builder() {}

        /**
         * Set the profile name to look up. If not set, the default resolution order applies
         * ({@code AWS_PROFILE}, {@code AWS_DEFAULT_PROFILE}, {@code "default"}).
         */
        public Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        /**
         * Use a pre-loaded {@link AwsProfileFile}. Mutually exclusive with {@link #configFile(Path)}
         * and {@link #credentialsFile(Path)}.
         */
        public Builder profileFile(AwsProfileFile profileFile) {
            this.profileFile = Objects.requireNonNull(profileFile, "profileFile");
            this.configFile = null;
            this.configFileSet = false;
            this.credentialsFile = null;
            this.credentialsFileSet = false;
            return this;
        }

        /**
         * Override the config file path. Mutually exclusive with {@link #profileFile(AwsProfileFile)}.
         * Pass {@code null} to explicitly disable reading a config file.
         */
        public Builder configFile(Path configFile) {
            this.profileFile = null;
            this.configFile = configFile;
            this.configFileSet = true;
            return this;
        }

        /**
         * Override the credentials file path. Mutually exclusive with {@link #profileFile(AwsProfileFile)}.
         * Pass {@code null} to explicitly disable reading a credentials file.
         */
        public Builder credentialsFile(Path credentialsFile) {
            this.profileFile = null;
            this.credentialsFile = credentialsFile;
            this.credentialsFileSet = true;
            return this;
        }

        /**
         * Register a credential-source handler. Handlers are tried in registration order; the
         * first handler that returns non-null for a given source wins.
         *
         * <p>If no handlers are registered before {@link #build()}, the resolver discovers
         * handlers via {@link java.util.ServiceLoader}. Calling this method replaces ServiceLoader discovery
         * entirely; only explicitly added handlers will be used.
         */
        public Builder addHandler(AwsConfigCredentialSourceHandler handler) {
            this.handlers.add(Objects.requireNonNull(handler, "handler"));
            return this;
        }

        /**
         * When {@code true}, credential sources that no handler claims are skipped and the next source in priority
         * order is attempted. When {@code false} (the default), an unhandled source causes an immediate error,
         * matching the AWS SDK shared-configuration specification's requirement that the highest-priority source
         * MUST be used.
         */
        public Builder ignoreUnhandledSources(boolean ignoreUnhandledSources) {
            this.ignoreUnhandledSources = ignoreUnhandledSources;
            return this;
        }

        /**
         * Build the resolver.
         */
        public AwsProfileCredentialsResolver build() {
            return new AwsProfileCredentialsResolver(this);
        }
    }
}
