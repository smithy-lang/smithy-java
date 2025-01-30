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
 */
record HttpResponseProtocolTestCase(
        HttpResponseTestCase responseTestCase,
        boolean isErrorTest,
        Supplier<ShapeBuilder<? extends SerializableStruct>> outputBuilder) {}
