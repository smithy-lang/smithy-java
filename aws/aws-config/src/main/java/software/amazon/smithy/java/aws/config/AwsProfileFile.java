/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A mutable, in-memory view of the AWS shared {@code config} and {@code credentials} files, merged
 * by profile name.
 *
 * <p>Behavior matches the AWS SDK shared-configuration specification:
 * <ul>
 *   <li>Files are UTF-8 encoded. Non-existent files are treated as empty; inaccessible files
 *       result in an {@link UncheckedIOException}.</li>
 *   <li>Default paths are {@code ~/.aws/config} and {@code ~/.aws/credentials}, overridable via
 *       the {@code AWS_CONFIG_FILE} and {@code AWS_SHARED_CREDENTIALS_FILE} environment variables.</li>
 *   <li>Critical syntax errors cause {@link ConfigFileParseException} to be thrown.</li>
 *   <li>Property keys are case-insensitive and are stored lower-cased.</li>
 *   <li>When a profile is present in both files, the two profiles are merged: properties defined
 *       in the credentials file take precedence over the same property in the configuration file.</li>
 *   <li>In the configuration file, {@code [profile default]} supersedes {@code [default]} when
 *       both are present.</li>
 * </ul>
 *
 * <p>Typical usage:
 * <pre>{@code
 * AwsProfileFile profileFile = AwsProfileFile.load();
 * AwsProfile defaultProfile = profileFile.profile("default");
 * if (defaultProfile != null) {
 *     String region = defaultProfile.property("region");
 * }
 *
 * // Pick up edits on disk. Mutates this instance in place.
 * profileFile.refresh();
 * }</pre>
 *
 * <p><b>Thread-safety:</b> reads after a call to {@link #refresh()} observe the new state
 * atomically via a {@code volatile} internal reference. Callers that hold references to
 * previously-returned profiles or profile lists are not affected by a later refresh; those views
 * reflect the state at the time they were obtained.
 */
public final class AwsProfileFile {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(AwsProfileFile.class);

    /** Environment variable overriding the config file path. */
    public static final String AWS_CONFIG_FILE_ENV = "AWS_CONFIG_FILE";

    /** Environment variable overriding the credentials file path. */
    public static final String AWS_SHARED_CREDENTIALS_FILE_ENV = "AWS_SHARED_CREDENTIALS_FILE";

    /** Context key for sharing a loaded profile file across providers in the chain. */
    public static final Context.Key<AwsProfileFile> CONTEXT_KEY = Context.key("awsProfileFile");

    private final Path configFile;
    private final Path credentialsFile;
    private volatile State state;

    private AwsProfileFile(
            Path configFile,
            Path credentialsFile,
            Map<String, AwsProfile> profiles,
            Map<String, AwsProfile> ssoSessions
    ) {
        this.configFile = configFile;
        this.credentialsFile = credentialsFile;
        this.state = State.of(profiles, ssoSessions);
    }

    /**
     * Load an {@link AwsProfileFile} from the default paths.
     *
     * <p>The default paths are:
     * <ul>
     *   <li>Config: the value of {@code AWS_CONFIG_FILE} if set, otherwise {@code ~/.aws/config}.</li>
     *   <li>Credentials: the value of {@code AWS_SHARED_CREDENTIALS_FILE} if set, otherwise
     *       {@code ~/.aws/credentials}.</li>
     * </ul>
     */
    public static AwsProfileFile load() {
        return builder().build();
    }

    /**
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the config file path this instance was loaded from, or {@code null} if none was set.
     */
    public Path configFile() {
        return configFile;
    }

    /**
     * @return the credentials file path this instance was loaded from, or {@code null} if none was set.
     */
    public Path credentialsFile() {
        return credentialsFile;
    }

    /**
     * @return an immutable, insertion-ordered list of all profiles in this snapshot. The list
     *         reflects the state at the time of the call; a subsequent {@link #refresh()} does not
     *         affect an already-returned list.
     */
    public List<AwsProfile> profiles() {
        return state.profiles;
    }

    /**
     * @return an immutable, insertion-ordered list of profile names in this snapshot.
     */
    public List<String> profileNames() {
        return state.profileNames;
    }

    /**
     * @return an immutable map of SSO session name to its profile-like data. Only populated from the
     *         configuration file; the credentials file does not support sso-session sections.
     */
    public Map<String, AwsProfile> ssoSessions() {
        return state.ssoSessions;
    }

    /**
     * Look up a profile by name.
     *
     * @param name the profile name.
     * @return the profile, or {@code null} if no profile by that name is present.
     */
    public AwsProfile profile(String name) {
        return state.byName.get(Objects.requireNonNull(name, "name"));
    }

    /**
     * Re-read the config and credentials files from the paths this snapshot was loaded from and
     * update this instance in place. Existing {@link AwsProfile} references previously handed out
     * are not mutated; subsequent calls to {@link #profile(String)}, {@link #profiles()}, and
     * {@link #profileNames()} reflect the new state.
     */
    public void refresh() {
        ProfileStandardizer.Result configResult = readAndStandardize(configFile, AwsConfigFileType.CONFIGURATION);
        ProfileStandardizer.Result credResult = readAndStandardize(credentialsFile, AwsConfigFileType.CREDENTIALS);
        this.state = State.of(mergeAcrossFiles(configResult.profiles(), credResult.profiles()),
                configResult.ssoSessions());
    }

    /**
     * Builder for {@link AwsProfileFile}.
     *
     * <p>If neither {@link #configFile(Path)} nor {@link #credentialsFile(Path)} is called, the
     * builder falls back to the defaults described on {@link AwsProfileFile#load()}. Calling either
     * setter with {@code null} disables that file entirely for this instance.
     */
    public static final class Builder {
        private Path explicitConfigFile;
        private Path explicitCredentialsFile;
        private boolean useDefaultConfigFile = true;
        private boolean useDefaultCredentialsFile = true;

        private Builder() {}

        /**
         * Use the given path as the config file. Pass {@code null} to disable reading a config file.
         */
        public Builder configFile(Path path) {
            this.explicitConfigFile = path;
            this.useDefaultConfigFile = false;
            return this;
        }

        /**
         * Use the given path as the credentials file. Pass {@code null} to disable reading a
         * credentials file.
         */
        public Builder credentialsFile(Path path) {
            this.explicitCredentialsFile = path;
            this.useDefaultCredentialsFile = false;
            return this;
        }

        /**
         * Build the {@link AwsProfileFile}.
         *
         * @throws UncheckedIOException if a file exists but cannot be read.
         * @throws ConfigFileParseException if either file contains a critical syntax error.
         */
        public AwsProfileFile build() {
            Path configPath = useDefaultConfigFile ? defaultConfigFilePath() : explicitConfigFile;
            Path credentialsPath = useDefaultCredentialsFile ? defaultCredentialsFilePath() : explicitCredentialsFile;

            ProfileStandardizer.Result configResult = readAndStandardize(configPath, AwsConfigFileType.CONFIGURATION);
            ProfileStandardizer.Result credResult = readAndStandardize(credentialsPath, AwsConfigFileType.CREDENTIALS);
            Map<String, AwsProfile> merged = mergeAcrossFiles(configResult.profiles(), credResult.profiles());

            // SSO sessions only come from the config file.
            return new AwsProfileFile(configPath, credentialsPath, merged, configResult.ssoSessions());
        }
    }

    private static Map<String, AwsProfile> mergeAcrossFiles(
            Map<String, AwsProfile> configProfiles,
            Map<String, AwsProfile> credProfiles
    ) {
        Map<String, AwsProfile> out = new LinkedHashMap<>();

        for (Map.Entry<String, AwsProfile> e : configProfiles.entrySet()) {
            String name = e.getKey();
            AwsProfile base = e.getValue();
            AwsProfile overlay = credProfiles.get(name);
            if (overlay == null) {
                out.put(name, base);
            } else {
                out.put(name, merge(base, overlay));
            }
        }
        for (Map.Entry<String, AwsProfile> e : credProfiles.entrySet()) {
            if (!out.containsKey(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static AwsProfile merge(AwsProfile base, AwsProfile overlay) {
        Map<String, String> props = new LinkedHashMap<>(base.properties());
        for (Map.Entry<String, String> e : overlay.properties().entrySet()) {
            String key = e.getKey();
            String oldValue = props.get(key);
            if (oldValue != null && !oldValue.equals(e.getValue())) {
                LOGGER.warn("Profile '{}' property '{}' from configuration file is shadowed by the "
                        + "credentials file.", base.name(), key);
            }
            props.put(key, e.getValue());
        }

        Map<String, Map<String, String>> subs = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : base.subProperties().entrySet()) {
            subs.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        for (Map.Entry<String, Map<String, String>> e : overlay.subProperties().entrySet()) {
            subs.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        return new AwsProfile(base.name(), props, subs);
    }

    private static ProfileStandardizer.Result readAndStandardize(Path path, AwsConfigFileType fileType) {
        if (path == null) {
            return new ProfileStandardizer.Result(Collections.emptyMap(), Collections.emptyMap());
        }
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            LOGGER.debug("AWS profile file does not exist: {}", path);
            return new ProfileStandardizer.Result(Collections.emptyMap(), Collections.emptyMap());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read AWS profile file: " + path, e);
        }
        try {
            var sections = AwsProfileFileParser.parse(content);
            return ProfileStandardizer.standardize(sections, fileType);
        } catch (ConfigFileParseException e) {
            throw new ConfigFileParseException(
                    e.lineNumber(),
                    e.getMessage() + " (file: " + path + ")");
        }
    }

    private static Path defaultConfigFilePath() {
        String override = System.getenv(AWS_CONFIG_FILE_ENV);
        if (override != null && !override.isEmpty()) {
            return AwsHomeResolver.expandTilde(override);
        }
        Path home = AwsHomeResolver.resolveHome();
        return home == null ? Paths.get(".aws", "config") : home.resolve(".aws").resolve("config");
    }

    private static Path defaultCredentialsFilePath() {
        String override = System.getenv(AWS_SHARED_CREDENTIALS_FILE_ENV);
        if (override != null && !override.isEmpty()) {
            return AwsHomeResolver.expandTilde(override);
        }
        Path home = AwsHomeResolver.resolveHome();
        return home == null ? Paths.get(".aws", "credentials") : home.resolve(".aws").resolve("credentials");
    }

    /**
     * Resolve the default config and credentials file paths without reading them.
     * Package-private for testing.
     */
    static ResolvedPaths resolveDefaultPaths(
            Function<String, String> envGetter,
            Function<String, String> propertyGetter
    ) {
        Path home = AwsHomeResolver.resolveHome(envGetter, propertyGetter);
        String configOverride = envGetter.apply(AWS_CONFIG_FILE_ENV);
        String credsOverride = envGetter.apply(AWS_SHARED_CREDENTIALS_FILE_ENV);
        Path config;
        if (configOverride != null && !configOverride.isEmpty()) {
            config = AwsHomeResolver.expandTilde(configOverride, home);
        } else {
            config = home == null ? Paths.get(".aws", "config") : home.resolve(".aws").resolve("config");
        }
        Path creds;
        if (credsOverride != null && !credsOverride.isEmpty()) {
            creds = AwsHomeResolver.expandTilde(credsOverride, home);
        } else {
            creds = home == null ? Paths.get(".aws", "credentials") : home.resolve(".aws").resolve("credentials");
        }
        return new ResolvedPaths(config, creds);
    }

    record ResolvedPaths(Path configLocation, Path credentialsLocation) {}

    @Override
    public String toString() {
        return "AwsProfileFile[configFile=" + configFile
                + ", credentialsFile=" + credentialsFile
                + ", profiles=" + state.profileNames + ']';
    }

    /**
     * Snapshot of the profile set; swapped atomically on {@link #refresh()}.
     */
    private record State(
            List<AwsProfile> profiles,
            List<String> profileNames,
            Map<String, AwsProfile> byName,
            Map<String, AwsProfile> ssoSessions) {
        static State of(Map<String, AwsProfile> ordered, Map<String, AwsProfile> ssoSessions) {
            List<AwsProfile> profiles = new ArrayList<>(ordered.values());
            List<String> names = new ArrayList<>(ordered.keySet());
            return new State(
                    Collections.unmodifiableList(profiles),
                    Collections.unmodifiableList(names),
                    Collections.unmodifiableMap(new LinkedHashMap<>(ordered)),
                    Collections.unmodifiableMap(ssoSessions));
        }
    }
}
