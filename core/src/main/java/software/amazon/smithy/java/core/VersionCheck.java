/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Validates that all Smithy Java modules on the classpath have compatible versions.
 *
 * <p>Mixing different versions of Smithy Java modules in the same application can cause
 * subtle runtime errors such as missing methods, class not found exceptions, or unexpected
 * behavior that are difficult to diagnose. This commonly happens when different dependencies
 * pull in different versions of the same module transitively. This check detects such
 * mismatches early, at class-load time, before any operation is executed.
 *
 * <p>This check runs once during class initialization of generated code. It discovers
 * all {@code META-INF/smithy-java/versions.properties} resources on the classpath and
 * verifies that all modules report the same version and that all versions are at least
 * as new as the version the code was generated against.
 *
 * <p>The check can be disabled by setting the system property
 * {@code smithy.java.skipVersionCheck} to {@code true}.
 */
public final class VersionCheck {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(VersionCheck.class);
    private static final String VERSIONS_RESOURCE = "META-INF/smithy-java/versions.properties";
    private static final String SKIP_PROPERTY = "smithy.java.skipVersionCheck";

    private VersionCheck() {}

    /**
     * Validates version compatibility of all Smithy Java modules on the classpath.
     *
     * @param codegenVersion the version the code was generated against
     * @throws IncompatibleVersionException if a version mismatch is detected
     */
    public static void check(String codegenVersion) {
        if (Boolean.getBoolean(SKIP_PROPERTY)) {
            LOGGER.warn("Smithy Java version compatibility check is disabled via '{}'. "
                    + "This is not recommended and should only be used as a temporary workaround. "
                    + "Running with mismatched module versions may cause unexpected runtime errors.",
                    SKIP_PROPERTY);
            return;
        }

        var modules = new ArrayList<String[]>();
        try {
            var urls = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(VERSIONS_RESOURCE);
            while (urls.hasMoreElements()) {
                var props = new Properties();
                try (var is = urls.nextElement().openStream()) {
                    props.load(is);
                }
                modules.add(new String[] {
                        props.getProperty("module", "unknown"),
                        props.getProperty("version", "unknown")
                });
            }
        } catch (IOException e) {
            // Don't fail startup if we can't read version resources.
            return;
        }

        if (modules.isEmpty()) {
            return;
        }

        var errors = new ArrayList<String>();

        // All modules must report the same version.
        var firstVersion = modules.get(0)[1];
        for (var module : modules) {
            if (!module[1].equals(firstVersion)) {
                errors.add("Version mismatch: module '" + modules.get(0)[0] + "' has version "
                        + firstVersion + " but module '" + module[0] + "' has version " + module[1]);
            }
        }

        // All module versions must be >= the codegen version.
        for (var module : modules) {
            if (compareVersions(module[1], codegenVersion) < 0) {
                errors.add("Module '" + module[0] + "' version " + module[1]
                        + " is older than the codegen version " + codegenVersion);
            }
        }

        if (!errors.isEmpty()) {
            // Build a nice error message to give the end-user all the details needed
            // to fix the issue.
            var sb = new StringBuilder("Smithy Java version compatibility check failed:\n");
            sb.append("  Generated with version: ").append(codegenVersion).append("\n");
            sb.append("  Modules on classpath:\n");
            for (var module : modules) {
                sb.append("    - ").append(module[0]).append(" = ").append(module[1]).append("\n");
            }
            sb.append("  Issues:\n");
            for (var error : errors) {
                sb.append("    - ").append(error).append("\n");
            }
            sb.append("  Fix: Align all smithy-java dependencies to the same version. ")
                    .append("If using Gradle, consider importing the BOM: ")
                    .append("platform('software.amazon.smithy.java:bom:")
                    .append(codegenVersion)
                    .append("')");
            throw new IncompatibleVersionException(sb.toString());
        }
    }

    private static int compareVersions(String v1, String v2) {
        var parts1 = v1.split("[.\\-]");
        var parts2 = v2.split("[.\\-]");
        var len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            var p1 = i < parts1.length ? parsePart(parts1[i]) : 0;
            var p2 = i < parts2.length ? parsePart(parts2[i]) : 0;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
