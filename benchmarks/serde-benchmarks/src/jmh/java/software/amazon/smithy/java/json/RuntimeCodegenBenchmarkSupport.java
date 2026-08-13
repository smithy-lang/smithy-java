/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenDiagnostics;

/**
 * Benchmark-only checks that generated measurements are not exercising fallback.
 */
public final class RuntimeCodegenBenchmarkSupport {
    private RuntimeCodegenBenchmarkSupport() {}

    public static void requireGenerated(JsonCodec codec) {
        var diagnostics = diagnostics(codec);
        if (diagnostics.successes() == 0 || diagnostics.failures() != 0) {
            throw new IllegalStateException("Runtime codegen publication failed: " + diagnostics);
        }
    }

    public static RuntimeCodegenDiagnostics.Snapshot diagnostics(JsonCodec codec) {
        JsonSettings settings = codec.settings();
        if (!(settings.provider() instanceof CodegenJsonSerdeProvider provider)) {
            throw new IllegalStateException("Runtime codegen provider is not active");
        }
        return provider.diagnostics(settings);
    }
}
