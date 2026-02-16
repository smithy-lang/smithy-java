/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static java.net.URLDecoder.decode;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.TestType;

@ProtocolTest(
        service = "aws.protocoltests.query#AwsQuery",
        testType = TestType.CLIENT)
public class AwsQueryProtocolTests {

    @HttpClientRequestTests
    @ProtocolTestFilter(
            skipTests = {
                    "SDKAppliedContentEncoding_awsQuery",
                    "SDKAppendsGzipAndIgnoresHttpProvidedEncoding_awsQuery",
            })
    public void requestTest(DataStream expected, DataStream actual) {
        String expectedStr = new String(
                ByteBufferUtils.getBytes(expected.asByteBuffer()),
                StandardCharsets.UTF_8);
        String actualStr = new String(
                ByteBufferUtils.getBytes(actual.asByteBuffer()),
                StandardCharsets.UTF_8);

        Map<String, String> expectedParams = parseFormUrlEncoded(expectedStr);
        Map<String, String> actualParams = parseFormUrlEncoded(actualStr);

        assertEquals(expectedParams, actualParams);
    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
            skipTests = {
                    "AwsQueryClientPopulatesDefaultsValuesWhenMissingInResponse",
            })
    public void responseTest(Runnable test) {
        test.run();
    }

    private Map<String, String> parseFormUrlEncoded(String body) {
        if (body == null || body.isEmpty()) {
            return new TreeMap<>();
        }
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        parts -> urlDecode(parts[0]),
                        parts -> parts.length > 1 ? urlDecode(parts[1]) : "",
                        (a, b) -> b,
                        TreeMap::new));
    }

    private String urlDecode(String value) {
        try {
            return decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
