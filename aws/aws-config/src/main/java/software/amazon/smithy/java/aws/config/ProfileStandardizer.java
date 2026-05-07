/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import software.amazon.smithy.java.aws.config.AwsProfileFileParser.RawProperty;
import software.amazon.smithy.java.aws.config.AwsProfileFileParser.RawSection;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Applies file-type-specific standardization to the raw sections produced by
 * {@link AwsProfileFileParser}, producing a map of profile name to {@link AwsProfile}.
 *
 * <p>Rules implemented (matching the AWS SDK shared-configuration SEP):
 * <ul>
 *   <li>Identifier validation. Profile and property names must match the {@code Identifier}
 *       character class. Invalid names are <i>silently dropped</i> and a warning is logged.</li>
 *   <li>File-type-specific profile rules. In the configuration file, non-default profiles must be
 *       declared as {@code [profile name]}; sections without the prefix (other than
 *       {@code [default]}) are silently dropped. In the credentials file, sections whose name
 *       starts with {@code "profile "} are silently dropped.</li>
 *   <li>{@code [profile default]} supersedes {@code [default]} within the configuration file. If
 *       both are present, the {@code [default]}-named sections are dropped entirely.</li>
 *   <li>Profiles duplicated within the same file have their properties merged. Duplicate property
 *       keys within the same profile use the later value (last-write-wins). Keys are stored
 *       lower-cased and compared case-insensitively.</li>
 *   <li>{@code sso-session} sections are accepted only in the configuration file, and only when a
 *       non-empty name is supplied. {@code services} sections follow the same rule.</li>
 * </ul>
 */
final class ProfileStandardizer {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ProfileStandardizer.class);

    /** Per the SEP revision log, identifiers may contain these characters. */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_\\-/.%@:+]+$");

    /** The result of standardizing a parsed file. */
    record Result(Map<String, AwsProfile> profiles, Map<String, AwsProfile> ssoSessions) {}

    /**
     * @param hadProfilePrefix True if this section was declared with a type prefix (for profile, "[profile x]").
     */
    private record Classification(SectionKind type, String name, boolean hadProfilePrefix) {}

    enum SectionKind {
        PROFILE, SSO_SESSION, SERVICES
    }

    private ProfileStandardizer() {}

    /**
     * Standardize a parsed file's raw sections.
     *
     * @param sections the raw sections, in file order.
     * @param fileType which of the two file types this content came from.
     * @return the standardized result containing profiles and sso sessions.
     */
    static Result standardize(List<RawSection> sections, AwsConfigFileType fileType) {
        boolean hasExplicitProfileDefaultInConfig = false;
        if (fileType == AwsConfigFileType.CONFIGURATION) {
            for (RawSection s : sections) {
                Classification c = classify(s, fileType, true);
                if (c != null && c.type == SectionKind.PROFILE && "default".equals(c.name) && c.hadProfilePrefix) {
                    hasExplicitProfileDefaultInConfig = true;
                    break;
                }
            }
        }

        Map<String, Map<String, String>> profileProps = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, String>>> profileSubs = new LinkedHashMap<>();
        Map<String, Map<String, String>> ssoProps = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, String>>> ssoSubs = new LinkedHashMap<>();

        for (RawSection s : sections) {
            Classification c = classify(s, fileType, false);
            if (c == null) {
                continue;
            }
            if (c.type == SectionKind.SSO_SESSION) {
                mergeProperties(s, c.name, ssoProps, ssoSubs);
                continue;
            }
            if (c.type != SectionKind.PROFILE) {
                continue;
            }
            if (fileType == AwsConfigFileType.CONFIGURATION
                    && "default".equals(c.name)
                    && !c.hadProfilePrefix
                    && hasExplicitProfileDefaultInConfig) {
                LOGGER.warn("Ignoring [default] section at line {}: [profile default] is also defined "
                        + "in the configuration file, which takes precedence.", s.lineNumber);
                continue;
            }
            mergeProperties(s, c.name, profileProps, profileSubs);
        }

        return new Result(buildProfiles(profileProps, profileSubs), buildProfiles(ssoProps, ssoSubs));
    }

    private static void mergeProperties(
            RawSection section,
            String name,
            Map<String, Map<String, String>> propsMap,
            Map<String, Map<String, Map<String, String>>> subsMap
    ) {
        Map<String, String> props = propsMap.computeIfAbsent(name, n -> new LinkedHashMap<>());
        Map<String, Map<String, String>> subs = subsMap.computeIfAbsent(name, n -> new LinkedHashMap<>());
        for (RawProperty p : section.properties.values()) {
            String key = lowerCase(p.key);
            if (!VALID_IDENTIFIER.matcher(key).matches()) {
                LOGGER.warn("Ignoring property at line {}: key contains invalid characters.", p.lineNumber);
                continue;
            }
            if (p.subProperties.isEmpty()) {
                props.put(key, p.value);
                subs.remove(key);
            } else {
                props.remove(key);
                Map<String, String> subMap = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : p.subProperties.entrySet()) {
                    String subKey = lowerCase(e.getKey());
                    if (!VALID_IDENTIFIER.matcher(subKey).matches()) {
                        LOGGER.warn("Ignoring sub-property at line {}: key contains invalid characters.",
                                p.lineNumber);
                        continue;
                    }
                    subMap.put(subKey, e.getValue());
                }
                subs.put(key, subMap);
            }
        }
    }

    private static Map<String, AwsProfile> buildProfiles(
            Map<String, Map<String, String>> propsMap,
            Map<String, Map<String, Map<String, String>>> subsMap
    ) {
        Map<String, AwsProfile> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : propsMap.entrySet()) {
            Map<String, Map<String, String>> subs = subsMap.getOrDefault(e.getKey(), Collections.emptyMap());
            out.put(e.getKey(), new AwsProfile(e.getKey(), e.getValue(), subs));
        }
        return out;
    }

    private static Classification classify(RawSection section, AwsConfigFileType fileType, boolean silent) {
        String raw = section.rawHeader;
        if (raw.isEmpty()) {
            if (!silent) {
                LOGGER.warn("Ignoring section at line {}: empty section name.", section.lineNumber);
            }
            return null;
        }

        String[] parts = raw.split("\\s+", 2);
        String typeToken = parts[0];
        String nameToken = parts.length > 1 ? parts[1].strip() : "";

        // Type-prefixed section types.
        if ("sso-session".equals(typeToken) || "services".equals(typeToken)) {
            if (fileType == AwsConfigFileType.CREDENTIALS) {
                if (!silent) {
                    LOGGER.warn("Ignoring [{} ...] section at line {}: not allowed in credentials file.",
                            typeToken,
                            section.lineNumber);
                }
                return null;
            } else if (nameToken.isEmpty()) {
                if (!silent) {
                    LOGGER.warn("Ignoring [{}] section at line {}: no name specified.", typeToken, section.lineNumber);
                }
                return null;
            } else if (!VALID_IDENTIFIER.matcher(nameToken).matches()) {
                if (!silent) {
                    LOGGER.warn("Ignoring [{} {}] section at line {}: name contains invalid characters.",
                            typeToken,
                            nameToken,
                            section.lineNumber);
                }
                return null;
            }

            SectionKind kind = "sso-session".equals(typeToken) ? SectionKind.SSO_SESSION : SectionKind.SERVICES;
            return new Classification(kind, nameToken, false);
        } else if ("profile".equals(typeToken)) {
            // A section of the form "[profile NAME]".
            if (fileType == AwsConfigFileType.CREDENTIALS) {
                if (!silent) {
                    LOGGER.warn("Ignoring section at line {}: profile names in the credentials file "
                            + "must not start with 'profile '.", section.lineNumber);
                }
                return null;
            }
            if (nameToken.isEmpty()) {
                if (!silent) {
                    LOGGER.warn("Ignoring [profile] section at line {}: no profile name specified.",
                            section.lineNumber);
                }
                return null;
            }
            if (!VALID_IDENTIFIER.matcher(nameToken).matches()) {
                if (!silent) {
                    LOGGER.warn("Ignoring [profile {}] section at line {}: name contains invalid characters.",
                            nameToken,
                            section.lineNumber);
                }
                return null;
            }
            return new Classification(SectionKind.PROFILE, nameToken, true);
        }

        // Plain section: just an identifier. Use cases:
        //  - Credentials: any valid profile name.
        //  - Configuration: only [default] is valid without the "profile " prefix.
        if (parts.length > 1) {
            // "foo bar" with an unknown type token -> invalid.
            if (!silent) {
                LOGGER.warn("Ignoring section at line {}: unknown section type '{}'.", section.lineNumber, typeToken);
            }
            return null;
        } else if (!VALID_IDENTIFIER.matcher(typeToken).matches()) {
            if (!silent) {
                LOGGER.warn("Ignoring section at line {}: profile name contains invalid characters.",
                        section.lineNumber);
            }
            return null;
        } else if (fileType == AwsConfigFileType.CONFIGURATION) {
            if (!"default".equals(typeToken)) {
                if (!silent) {
                    LOGGER.warn("Ignoring [{}] section at line {}: in the configuration file only [default] "
                            + "may omit the 'profile' prefix.", typeToken, section.lineNumber);
                }
                return null;
            }
            return new Classification(SectionKind.PROFILE, "default", false);
        }

        // Credentials file: any valid identifier is a profile name.
        return new Classification(SectionKind.PROFILE, typeToken, false);
    }

    private static String lowerCase(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
