/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Validates that all Smithy Java modules on the classpath have compatible versions.
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
            throw new IncompatibleVersionException(
                    "Smithy Java version compatibility check failed:\n  - "
                            + String.join("\n  - ", errors));
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
