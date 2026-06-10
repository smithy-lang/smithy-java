/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

/**
 * Identifies which client model a protocol test invocation exercises.
 *
 * <p>Client protocol tests run once per mode so both the codegen path (strongly-typed generated shapes) and the
 * dynamic path ({@code DynamicClient} / document-backed shapes) are validated against the same Smithy protocol test
 * cases. The dynamic path uses untyped {@code Document} serialization/deserialization, which is exercised far less
 * by hand-written tests, so running it here guards that whole code path.
 */
enum TestMode {
    CODEGEN("codegen"),
    DYNAMIC("dynamic");

    private final String label;

    TestMode(String label) {
        this.label = label;
    }

    /** Short label used in test display names, e.g. {@code SomeTestId [dynamic]}. */
    String label() {
        return label;
    }
}
