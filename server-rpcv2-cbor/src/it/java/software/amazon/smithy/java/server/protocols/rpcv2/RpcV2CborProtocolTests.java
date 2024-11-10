/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.rpcv2;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import software.amazon.java.runtime.cbor.CborComparator;
import software.amazon.smithy.java.protocoltests.harness.HttpServerRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpServerResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.TestType;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

@ProtocolTest(
    service = "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol",
    testType = TestType.SERVER
)
public class RpcV2CborProtocolTests {

    @HttpServerRequestTests
    @ProtocolTestFilter(
        skipTests = {
            //TODO fix empty body handling in the deserializer
            "no_input",
            "NoInputServerAllowsEmptyCbor",
            "NoInputServerAllowsEmptyBody",
            "empty_input_no_body",
            "empty_input_no_body_has_accept",
            //The test is incorrect. TODO fix the protocol test.
            "RpcV2CborServerPopulatesDefaultsWhenMissingInRequestBody"
        }
    )
    public void requestTest(Runnable test) {
        test.run();
    }

    @HttpServerResponseTests
    @ProtocolTestFilter(
        skipTests = {
            "no_output", //TODO genuine bug, fix
            //Similar as above, test is incorrect TODO fix the protocol test.
            "RpcV2CborServerPopulatesDefaultsInResponseWhenMissingInParams"
        }
    )
    public void responseTest(DataStream expected, DataStream actual) {
        assertThat(expected.hasKnownLength())
            .isTrue()
            .isSameAs(actual.hasKnownLength());
        CborComparator.assertEquals(expected.waitForByteBuffer(), actual.waitForByteBuffer());
    }

}
