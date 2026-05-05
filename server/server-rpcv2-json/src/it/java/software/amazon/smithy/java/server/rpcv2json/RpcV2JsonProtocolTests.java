/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.rpcv2json;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.HttpServerRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpServerResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.TestType;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

@ProtocolTest(
        service = "smithy.protocoltests.rpcv2Json#RpcV2JsonProtocol",
        testType = TestType.SERVER)
public class RpcV2JsonProtocolTests {

    @HttpServerRequestTests
    @ProtocolTestFilter(
            skipTests = {
                    // TODO fix empty body handling in the deserializer
                    "RpcV2JsonRequestNoInput",
                    "RpcV2JsonRequestNoInputServerAllowsEmptyBody",
                    "RpcV2JsonRequestEmptyInputNoBody",
                    // TODO fix the protocol test
                    "RpcV2JsonRequestServerPopulatesDefaultsWhenMissingInRequestBody"
            })
    public void requestTest(Runnable test) {
        test.run();
    }

    @HttpServerResponseTests
    @ProtocolTestFilter(
            skipTests = {
                    "RpcV2JsonResponseNoOutput", // TODO genuine bug, fix
                    // TODO fix the protocol test
                    "RpcV2JsonResponseServerPopulatesDefaultsInResponseWhenMissingInParams",
                    // Error serialization doesn't include __type so the below fail
                    "RpcV2JsonResponseInvalidGreetingError",
                    "RpcV2JsonResponseComplexError",
                    "RpcV2JsonResponseEmptyComplexError"
            })
    public void responseTest(DataStream expected, DataStream actual) {
        assertThat(expected.hasKnownLength())
                .isTrue()
                .isSameAs(actual.hasKnownLength());
        Node expectedNode = ObjectNode.objectNode();
        if (expected.contentLength() != 0) {
            expectedNode = Node.parse(new String(ByteBufferUtils.getBytes(expected.asByteBuffer()),
                    StandardCharsets.UTF_8));
        }
        Node actualNode = ObjectNode.objectNode();
        if (actual.contentLength() != 0) {
            actualNode = Node.parse(new String(ByteBufferUtils.getBytes(actual.asByteBuffer()),
                    StandardCharsets.UTF_8));
        }
        assertEquals(expectedNode, actualNode);
    }
}
