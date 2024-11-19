/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rpcv2;

import software.amazon.java.cbor.CborComparator;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.TestType;

@ProtocolTest(
    service = "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol",
    testType = TestType.CLIENT
)
@ProtocolTestFilter(skipOperations = {})
public class RpcV2CborProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
        skipTests = {
            // this test is broken and needs to be fixed in smithy
            "RpcV2CborClientPopulatesDefaultValuesInInput",
            // clientOptional is not respected for client-generated shapes yet
            "RpcV2CborClientSkipsTopLevelDefaultValuesInInput",
            "RpcV2CborClientUsesExplicitlyProvidedMemberValues",
            "RpcV2CborClientIgnoresNonTopLevelDefaultsOnMembersWithClientOptional",
            "RpcV2CborClientUsesExplicitlyProvidedMemberValuesOverDefaults",
        }
    )
    public void requestTest(DataStream expected, DataStream actual) {
        CborComparator.assertEquals(expected.waitForByteBuffer(), actual.waitForByteBuffer());
    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
        skipTests = {
            "RpcV2CborDateTimeWithFractionalSeconds",
            "RpcV2CborClientPopulatesDefaultsValuesWhenMissingInResponse",
            //Errors are not yet implemented properly
            "RpcV2CborInvalidGreetingError",
            "RpcV2CborComplexError",
            "RpcV2CborEmptyComplexError"
        }
    )
    public void responseTest(Runnable test) {
        test.run();
    }
}
