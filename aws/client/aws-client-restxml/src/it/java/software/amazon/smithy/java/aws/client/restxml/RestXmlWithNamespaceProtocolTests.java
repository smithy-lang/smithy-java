/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.restxml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.StringBuildingSubscriber;
import software.amazon.smithy.java.protocoltests.harness.TestType;

@ProtocolTest(
        service = "aws.protocoltests.restxml.xmlns#RestXmlWithNamespace",
        testType = TestType.CLIENT)
public class RestXmlWithNamespaceProtocolTests {
    @HttpClientRequestTests
    public void requestTest(DataStream expected, DataStream actual) {
        if (expected.contentLength() != 0) {
            var a = new String(ByteBufferUtils.getBytes(actual.asByteBuffer()), StandardCharsets.UTF_8);
            var b = new String(ByteBufferUtils.getBytes(expected.asByteBuffer()), StandardCharsets.UTF_8);
            if ("application/xml".equals(expected.contentType())) {
                if (!RestXmlProtocolTests.XMLComparator.compareXMLStrings(a, b)) {
                    assertThat(a, equalTo(b));
                }
            } else {
                assertEquals(a, b);
            }
        } else if (expected.contentType() != null) {
            assertEquals("", new StringBuildingSubscriber(actual).getResult());
        }
    }

    @HttpClientResponseTests
    public void responseTest(Runnable test) {
        test.run();
    }
}
