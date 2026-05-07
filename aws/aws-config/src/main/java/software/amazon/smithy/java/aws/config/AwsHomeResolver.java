/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Function;

/**
 * Resolves the user's home directory using the same chain of environment variables described in
 * the AWS SDK shared-configuration specification, and supports the tilde-expansion syntax used in
 * the {@code AWS_CONFIG_FILE} / {@code AWS_SHARED_CREDENTIALS_FILE} environment variables.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>The {@code HOME} environment variable, on any platform.</li>
 *   <li>On Windows platforms only: the {@code USERPROFILE} environment variable.</li>
 *   <li>On Windows platforms only: the concatenation of {@code HOMEDRIVE} and {@code HOMEPATH}.</li>
 *   <li>The {@code user.home} system property (the language-specific fallback permitted by the SEP).</li>
 * </ol>
 *
 * <p>When the platform cannot be determined, the Windows-specific variables are also inspected on
 * non-Windows platforms, as allowed by the spec.
 */
final class AwsHomeResolver {

    private AwsHomeResolver() {}

    /**
     * Resolve the current user's home directory using the default sources ({@link System#getenv(String)},
     * {@link System#getProperty(String)}).
     *
     * @return the resolved home directory, or {@code null} if it could not be determined.
     */
    static Path resolveHome() {
        return resolveHome(System::getenv, System::getProperty);
    }

    /**
     * Testable variant of {@link #resolveHome()} that takes injectable env and property getters.
     *
     * @param envGetter environment variable lookup.
     * @param propertyGetter system property lookup (used for {@code os.name} and {@code user.home}).
     * @return the resolved home directory, or {@code null} if none of the sources yielded a value.
     */
    static Path resolveHome(Function<String, String> envGetter, Function<String, String> propertyGetter) {
        String home = envGetter.apply("HOME");
        if (home != null && !home.isEmpty()) {
            return Paths.get(home);
        }

        boolean isWindows = isWindows(propertyGetter.apply("os.name"));
        boolean platformUnknown = propertyGetter.apply("os.name") == null;
        if (isWindows || platformUnknown) {
            String userProfile = envGetter.apply("USERPROFILE");
            if (userProfile != null && !userProfile.isEmpty()) {
                return Paths.get(userProfile);
            }
            String homeDrive = envGetter.apply("HOMEDRIVE");
            String homePath = envGetter.apply("HOMEPATH");
            if (homeDrive != null && !homeDrive.isEmpty() && homePath != null && !homePath.isEmpty()) {
                return Paths.get(homeDrive + homePath);
            }
        }

        String userHome = propertyGetter.apply("user.home");
        if (userHome != null && !userHome.isEmpty()) {
            return Paths.get(userHome);
        }

        return null;
    }

    /**
     * Expand a leading {@code ~} or {@code ~/} in a path using the resolved home directory.
     *
     * <p>If the path does not begin with {@code ~}, it is returned unchanged. If the path begins
     * with {@code ~} but home cannot be resolved, the path is returned unchanged (the file will
     * simply fail to open later, which matches the SEP's "treat as empty" rule for inaccessible
     * files).
     *
     * <p>This implementation does not support the {@code ~username/} form, which the SEP marks as
     * a <i>should</i> rather than a <i>must</i>.
     *
     * @param rawPath a path potentially beginning with a tilde.
     * @return the expanded path.
     */
    static Path expandTilde(String rawPath) {
        return expandTilde(rawPath, resolveHome());
    }

    static Path expandTilde(String rawPath, Path home) {
        if (rawPath == null || rawPath.isEmpty()) {
            return null;
        } else if (rawPath.charAt(0) != '~' || home == null) {
            return Paths.get(rawPath);
        } else if (rawPath.length() == 1) {
            return home;
        } else {
            char sep = rawPath.charAt(1);
            if (sep == '/' || sep == '\\') {
                String rest = rawPath.substring(2);
                return rest.isEmpty() ? home : home.resolve(rest);
            }
            // "~username/..." — unsupported; leave alone per the SEP's "should, not must" guidance.
            return Paths.get(rawPath);
        }
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("windows");
    }
}
