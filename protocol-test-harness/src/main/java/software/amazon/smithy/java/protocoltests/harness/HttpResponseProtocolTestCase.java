/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.function.Supplier;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;

/**
 * Data class holding information needed to execute a response protocol test for a given operation.
 * @param responseTestCase The smithy {@link HttpResponseTestCase}
 * @param isErrorTest Does this test an error response
 * @param outputBuilder {@link Supplier} to a {@link ShapeBuilder} of the operation output, which can also be an error.
 * @param dynamicOutputBuilder {@link Supplier} to a document-backed {@link ShapeBuilder} of the operation output (or
 *     error), used when running response tests through the {@code DynamicClient} path. Null when unavailable.
 */
record HttpResponseProtocolTestCase(
        HttpResponseTestCase responseTestCase,
        boolean isErrorTest,
        Supplier<ShapeBuilder<? extends SerializableStruct>> outputBuilder,
        Supplier<ShapeBuilder<? extends SerializableStruct>> dynamicOutputBuilder) {

    /**
     * Get the output builder supplier for a given {@link TestMode}, or null if that mode is unavailable.
     */
    Supplier<ShapeBuilder<? extends SerializableStruct>> outputBuilder(TestMode mode) {
        return mode == TestMode.DYNAMIC ? dynamicOutputBuilder : outputBuilder;
    }
}
