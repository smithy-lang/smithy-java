/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;

/**
 * Fuzz test for the AWS config file parser. Ensures no input can cause unexpected exceptions,
 * OOM, or infinite loops. The only acceptable exception is {@link ConfigFileParseException}.
 */
class AwsProfileFileParserFuzzTest {

    @FuzzTest(maxDuration = "5m")
    void fuzzParser(byte[] data) {
        try {
            AwsProfileFileParser.parse(new String(data, StandardCharsets.UTF_8));
        } catch (ConfigFileParseException expected) {
            // Valid failure mode — malformed input.
        }
    }
}
