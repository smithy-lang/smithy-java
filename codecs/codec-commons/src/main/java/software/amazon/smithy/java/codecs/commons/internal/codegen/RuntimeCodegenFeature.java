/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import java.util.Locale;
import software.amazon.smithy.java.core.serde.RuntimeCodegenMode;
import software.amazon.smithy.utils.SmithyInternalApi;

/** Resolves runtime codegen mode from caller settings and system properties. */
@SmithyInternalApi
public final class RuntimeCodegenFeature {
    private static final String PROPERTY = "smithy-java.runtime-codegen";

    private RuntimeCodegenFeature() {}

    public static boolean available() {
        return Runtime.version().feature() >= 25;
    }

    public static boolean enabled(String backend) {
        return resolve(null, backend) != RuntimeCodegenMode.DISABLED;
    }

    public static boolean strict(String backend) {
        return resolve(null, backend) == RuntimeCodegenMode.STRICT;
    }

    /** Resolves the effective mode for a backend. */
    public static RuntimeCodegenMode resolve(RuntimeCodegenMode requested, String backend) {
        RuntimeCodegenMode configured = configured(backend);
        if (requested == RuntimeCodegenMode.DISABLED
                || configured == RuntimeCodegenMode.DISABLED
                || (requested == null && configured == null)) {
            return RuntimeCodegenMode.DISABLED;
        }
        RuntimeCodegenMode resolved = requested == RuntimeCodegenMode.STRICT || configured == RuntimeCodegenMode.STRICT
                ? RuntimeCodegenMode.STRICT
                : RuntimeCodegenMode.ENABLED;
        if (available()) {
            return resolved;
        }
        if (resolved == RuntimeCodegenMode.STRICT) {
            throw new IllegalStateException(
                    "Runtime code generation requires Java 25 or later; strict mode forbids fallback");
        }
        return RuntimeCodegenMode.DISABLED;
    }

    private static RuntimeCodegenMode configured(String backend) {
        RuntimeCodegenMode scoped = parse(PROPERTY + "." + backend);
        return scoped != null ? scoped : parse(PROPERTY);
    }

    private static RuntimeCodegenMode parse(String property) {
        String value;
        try {
            value = System.getProperty(property);
        } catch (SecurityException ignored) {
            return null;
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RuntimeCodegenMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Fail invalid operational configuration at first use.
            throw new IllegalArgumentException(
                    "Invalid value '" + value + "' for " + property
                            + "; expected one of disabled, enabled, strict");
        }
    }
}
