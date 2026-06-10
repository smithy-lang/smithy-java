/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.List;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocoltests.traits.HttpMalformedRequestTestCase;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;
import software.amazon.smithy.protocoltests.traits.eventstream.EventStreamTestCase;

/**
 * Data class holding information needed to execute a protocol test for a given operation.
 *
 * @param id Smithy {@link ShapeId} of the operation.
 * @param serviceId Smithy {@link ShapeId} of the Service this operation belongs to.
 * @param operationModel Generated (codegen) operation model class. This can be used to get input/output builders.
 * @param dynamicOperationModel Document-backed operation model (DynamicClient path). Null when no dynamic harness is
 *     available (e.g. server tests). When present, client tests are run a second time through this model so the
 *     untyped document serialization/deserialization paths get the same protocol-test coverage as codegen.
 * @param requestTestCases A list of request test cases attached to the operation.
 * @param responseTestCases A list of response test cases attached to the operation.
 * @param malformedRequestTestCases A list of malformed test cases attached to the operation.
 */
record HttpTestOperation(
        ShapeId id,
        ShapeId serviceId,
        ApiOperation<?, ?> operationModel,
        ApiOperation<?, ?> dynamicOperationModel,
        List<HttpRequestTestCase> requestTestCases,
        List<HttpResponseProtocolTestCase> responseTestCases,
        List<HttpMalformedRequestTestCase> malformedRequestTestCases,
        List<EventStreamTestCase> eventStreamTestCases) {

    /**
     * Get the operation model for a given {@link TestMode}, or null if that mode is not available.
     */
    ApiOperation<?, ?> operationModel(TestMode mode) {
        return mode == TestMode.DYNAMIC ? dynamicOperationModel : operationModel;
    }
}
