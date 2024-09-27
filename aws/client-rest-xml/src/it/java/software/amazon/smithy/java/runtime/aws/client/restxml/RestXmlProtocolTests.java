/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.client.restxml;

import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;

@ProtocolTest(service = "aws.protocoltests.restxml#RestXml")
@ProtocolTestFilter(
    skipOperations = {

    }
)
public class RestXmlProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
        skipTests = {

        }
    )
    public void requestTest(Runnable test) throws Exception {
        test.run();
    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
        skipTests = {
            // TODO: Need to split based on comma
            "InputAndOutputWithNumericHeaders",
            "InputAndOutputWithBooleanHeaders",
            "InputAndOutputWithTimestampHeaders"
        }
    )
    public void responseTest(Runnable test) throws Exception {
        test.run();
    }
}
