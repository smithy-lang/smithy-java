/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.*;
import software.amazon.smithy.model.node.Node;

@ProtocolTest(
        service = "aws.protocoltests.restjson#RestJson",
        testType = TestType.SERVER)
public class AwsRestJson1ProtocolTests {

    @HttpServerRequestTests
    @ProtocolTestFilter(
            skipTests = {
                    "RestJsonNullAndEmptyHeaders",
                    "RestJsonSupportsNaNFloatLabels",
                    "RestJsonOmitsEmptyListQueryValues",
                    "RestJsonQueryIdempotencyTokenAutoFill",
                    "RestJsonQueryPrecedence",
                    "RestJsonQueryParamsStringListMap",
                    "RestJsonRecursiveShapes",
                    "RestJsonSerializesDenseSetMap",
                    "RestJsonEndpointTraitWithHostLabel",
                    "RestJsonHostWithPath",
                    "RestJsonHttpWithEmptyStructurePayload",
                    "RestJsonHttpWithHeadersButNoPayload",
                    "RestJsonHttpPostWithNoModeledBody",
                    "RestJsonHttpWithPostHeaderMemberNoModeledBody",
                    "RestJsonHttpPostWithNoInput",
                    "SDKAppliedContentEncoding_restJson1",
                    "SDKAppendedGzipAfterProvidedEncoding_restJson1",
                    "RestJsonClientPopulatesDefaultValuesInInput",
                    "RestJsonClientUsesExplicitlyProvidedMemberValuesOverDefaults",
                    "RestJsonMustSupportParametersInContentType", //hangs on the client side some reason
                    "RestJsonServerPopulatesDefaultsWhenMissingInRequestBody",

                    // Header splitting needs work.
                    "RestJsonInputAndOutputWithQuotedStringHeaders",
            },
            skipOperations = {
                    "aws.protocoltests.restjson#DocumentType",
                    "aws.protocoltests.restjson#DocumentTypeAsMapValue",
                    "aws.protocoltests.restjson#DocumentTypeAsPayload",
                    "aws.protocoltests.restjson#JsonUnions",
                    "aws.protocoltests.restjson#PostUnionWithJsonName",
            })
    public void requestTest(Runnable test) {
        test.run();
    }

    @HttpServerResponseTests
    @ProtocolTestFilter(
            skipTests = {
                    "MediaTypeHeaderOutputBase64",
                    "RestJsonDeserializeIgnoreType",
                    "RestJsonDateTimeWithNegativeOffset",
                    "RestJsonDateTimeWithPositiveOffset",
                    "RestJsonClientPopulatesDefaultsValuesWhenMissingInResponse",
                    "RestJsonClientIgnoresDefaultValuesIfMemberValuesArePresentInResponse",
                    "RestJsonClientPopulatesNestedDefaultsWhenMissingInResponseBody",
                    "RestJsonDoesntDeserializeNullStructureValues",
                    "RestJsonEnumPayloadResponse",
                    "RestJsonStreamingTraitsWithBlob",
                    "RestJsonStreamingTraitsWithMediaTypeWithBlob",
                    "RestJsonDeserializesDenseSetMapAndSkipsNull",
                    "RestJsonServerPopulatesDefaultsInResponseWhenMissingInParams",
                    // These can be fixed after https://github.com/smithy-lang/smithy-java/blob/main/http-binding/src/main/java/software/amazon/smithy/java/http/binding/HttpBindingSerializer.java#L109
                    "RestJsonInvalidGreetingError",
                    "RestJsonComplexErrorWithNoMessage",
                    "RestJsonEmptyComplexErrorWithNoMessage",
                    //TODO this breaks because of Validation and errorCorrection doesn't handle that.
                    "RestJsonServerPopulatesNestedDefaultValuesWhenMissingInInResponseParams"

            })
    public void responseTest(DataStream expected, DataStream actual) {
        assertThat(expected.hasKnownLength())
                .isTrue()
                .isSameAs(actual.hasKnownLength());

        String actualJson = new String(ByteBufferUtils.getBytes(actual.waitForByteBuffer()), StandardCharsets.UTF_8);
        String expectedJson = new String(
                ByteBufferUtils.getBytes(expected.waitForByteBuffer()),
                StandardCharsets.UTF_8);
        if (expected.contentLength() == 0) {
            assertThat(actualJson).isIn("", "{}");
            return;
        }

        if (expected.contentType().equals("application/json")) {
            var expectedNode = Node.parse(expectedJson);
            var actualNode = Node.parse(actualJson);
            Node.assertEquals(actualNode, expectedNode);
        } else {
            assertThat(actualJson).isEqualTo(expectedJson);
        }
    }

}
