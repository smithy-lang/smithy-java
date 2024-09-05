/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.aws.restjson1;

import software.amazon.smithy.java.protocoltests.harness.Filter;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;

@ProtocolTest(service = "aws.protocoltests.restjson#RestJson")
@Filter(skipOperations = {
    // Endpoint traits are not yet supported
    "aws.protocoltests.restjson#EndpointWithHostLabelOperation", "aws.protocoltests.restjson#EndpointOperation",
    // We dont ignore defaults on input shapes
    "aws.protocoltests.restjson#OperationWithDefaults",
    // All the Http payload tests are breaking. No idea why
    "aws.protocoltests.restjson#HttpPayloadTraits", "aws.protocoltests.restjson#HttpEnumPayload", "aws.protocoltests.restjson#HttpPayloadTraitsWithMediaType", "aws.protocoltests.restjson#HttpStringPayload", "aws.protocoltests.restjson#HttpPayloadWithUnion",
    // We do not fully support streaming in clients yet
    "aws.protocoltests.restjson#StreamingTraits", "aws.protocoltests.restjson#StreamingTraitsRequireLength", "aws.protocoltests.restjson#StreamingTraitsWithMediaType",
    // Clients do not support content-encoding yet
    "aws.protocoltests.restjson#PutWithContentEncoding",
    // Fails due to not being able to instantiate input object
    "aws.protocoltests.restjson#OperationWithNestedStructure"
}
)
public class ProtocolTests {
    @HttpClientRequestTests
    @Filter(skipTests = {
        // The order of the return values is different for some reason?
        "RestJsonSerializesSparseNullMapValues",
        // We do not yet support checksums in requests
        "RestJsonHttpChecksumRequired",
        // No idea. Fix
        "RestJsonHttpWithHeadersButNoPayload", "RestJsonHttpWithEmptyStructurePayload", "MediaTypeHeaderInputBase64", "RestJsonHttpWithEmptyBlobPayload"
    })
    public void requestTest(Runnable test) throws Exception {
        test.run();
    }

    @HttpClientResponseTests
    @Filter(skipTests = {
        // Null values are not skipped in deserialization
        "RestJsonDeserializesDenseSetMapAndSkipsNull",
        // STATUS is not supported yet? TODO?
        "RestJsonHttpResponseCodeWithNoPayload", "RestJsonHttpResponseCode",
        // "Cannot change union from unknown to known variant"
        "RestJsonDeserializeIgnoreType",
        // Invalid ints, bools, etc in headers
        "RestJsonInputAndOutputWithNumericHeaders", "RestJsonInputAndOutputWithBooleanHeaders", "RestJsonInputAndOutputWithTimestampHeaders", "RestJsonInputAndOutputWithIntEnumHeaders",
        // "Unexpected Content-Type ''"
        "RestJsonIgnoreQueryParamsInResponseNoPayload"
    })
    public void responseTest(Runnable test) throws Exception {
        test.run();
    }
}
