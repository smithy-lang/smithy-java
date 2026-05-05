/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rpcv2json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.StringBuildingSubscriber;
import software.amazon.smithy.java.protocoltests.harness.TestType;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

@ProtocolTest(
        service = "smithy.protocoltests.rpcv2Json#RpcV2JsonProtocol",
        testType = TestType.CLIENT)
public class RpcV2JsonProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
            skipTests = {
                    // clientOptional is not respected for client-generated shapes yet
                    "RpcV2JsonRequestClientSkipsTopLevelDefaultValuesInInput",
                    "RpcV2JsonRequestClientPopulatesDefaultValuesInInput",
                    "RpcV2JsonRequestClientUsesExplicitlyProvidedMemberValuesOverDefaults",
                    "RpcV2JsonRequestClientIgnoresNonTopLevelDefaultsOnMembersWithClientOptional",
            })
    public void requestTest(DataStream expected, DataStream actual) {
        Node expectedNode = ObjectNode.objectNode();
        if (expected.contentLength() != 0) {
            expectedNode = Node.parse(new String(ByteBufferUtils.getBytes(expected.asByteBuffer()),
                    StandardCharsets.UTF_8));
        }
        Node actualNode = ObjectNode.objectNode();
        if (actual.contentLength() != 0) {
            actualNode = Node.parse(new StringBuildingSubscriber(actual).getResult());
        }
        assertEquals(expectedNode, actualNode);
    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
            skipTests = {
                    "RpcV2JsonResponseClientPopulatesDefaultsValuesWhenMissingInResponse",
            })
    public void responseTest(Runnable test) {
        test.run();
    }
}
