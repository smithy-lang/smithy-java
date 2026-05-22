/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

/**
 * Contains the version of Smithy-Java.
 */
public final class Version {
    private static final String VERSION_RESOURCE_FILE = "version.properties";
    private static final String VERSION_PROPERTY = "version";

    /**
     * Smithy Java version number.
     * <p><strong>Note:</strong> This value is set by the build system.
     */
    public static final String VERSION = getVersion();

    static String getVersion() {
        try (InputStream is = Objects.requireNonNull(Version.class.getResourceAsStream(VERSION_RESOURCE_FILE))) {
            var properties = new Properties();
            properties.load(is);
            return properties.get(VERSION_PROPERTY).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Version() {}
}
