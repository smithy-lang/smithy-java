/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.gradle.api.GradleException;

/**
 * Represents the smithy-java Gradle plugin version.
 *
 * <p>The version is read from a {@code version.properties} resource file that is stamped
 * at build time with the current smithy-java version.
 */
public final class SmithyJavaVersion {
    public static final String VERSION_OVERRIDE_VAR = "smithyjava.gradle.version.override";
    public static final String VERSION = resolveVersion();

    private static final String VERSION_RESOURCE_NAME = "version.properties";
    private static final String VERSION_NUMBER_PROPERTY = "version";

    private SmithyJavaVersion() {}

    private static String resolveVersion() {
        try (InputStream inputStream = SmithyJavaVersion.class.getResourceAsStream(VERSION_RESOURCE_NAME)) {
            if (inputStream == null) {
                throw new GradleException(format("Version file '%s' not found.", VERSION_RESOURCE_NAME));
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.get(VERSION_NUMBER_PROPERTY).toString();

            // Allow version to be overridden via system property or env var (useful for tests)
            String overrideVersion = System.getProperty(VERSION_OVERRIDE_VAR);
            if (overrideVersion == null) {
                overrideVersion = System.getenv(VERSION_OVERRIDE_VAR);
            }
            if (overrideVersion != null) {
                return overrideVersion;
            }
            return version;
        } catch (IOException e) {
            throw new GradleException(format("Failed to read version file '%s'", VERSION_RESOURCE_NAME), e);
        }
    }
}
