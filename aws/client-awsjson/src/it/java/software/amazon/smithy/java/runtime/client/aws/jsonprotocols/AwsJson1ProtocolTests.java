/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.aws.jsonprotocols;

import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;

@ProtocolTest(service = "aws.protocoltests.json10#JsonRpc10")
@ProtocolTestFilter(skipOperations = {})
public class AwsJson1ProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
        skipTests = {
            // TODO: Endpoint trait isn't implemented.
            "AwsJson10EndpointTrait",

            // TODO: Implement hostLabel
            "AwsJson10EndpointTraitWithHostLabel",

            // TODO: implement content-encoding
            "SDKAppliedContentEncoding_awsJson1_0",
            "SDKAppendsGzipAndIgnoresHttpProvidedEncoding_awsJson1_0",

            // Skipping top-level input defaults isn't necessary in Smithy-Java given it uses builders and
            // the defaults don't impact nullability. This applies to the following tests.
            "AwsJson10ClientSkipsTopLevelDefaultValuesInInput",
            "AwsJson10ClientPopulatesDefaultValuesInInput",
            "AwsJson10ClientUsesExplicitlyProvidedMemberValuesOverDefaults",

            // Like above, but in smithy-java we populate the defaults but don't change the nullability.
            "AwsJson10ClientIgnoresNonTopLevelDefaultsOnMembersWithClientOptional",
        }
    )
    public void requestTest(Runnable test) throws Exception {
        test.run();
    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
        skipTests = {}
    )
    public void responseTest(Runnable test) throws Exception {
        test.run();
    }
}
