/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.List;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocoltests.traits.HttpMalformedRequestTestCase;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;

/**
 * Data class holding information needed to execute a protocol test for a given operation.
 *
 * @param id Smithy {@link ShapeId} of the operation.
 * @param serviceId Smithy {@link ShapeId} of the Service this operation belongs to.
 * @param operationModel Generate operation model class. This can be used to get input/output builders.
 * @param requestTestCases A list of request test cases attached to the operation.
 * @param responseTestCases A list of response test cases attached to the operation.
 * @param malformedRequestTestCases A list of malformed test cases attached to the operation.
 */
record HttpTestOperation(
    ShapeId id,
    ShapeId serviceId,
    ApiOperation<?, ?> operationModel,
    List<HttpRequestTestCase> requestTestCases,
    List<HttpResponseProtocolTestCase> responseTestCases,
    List<HttpMalformedRequestTestCase> malformedRequestTestCases
) {}
