/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AwsRulesFastPathTest {

    @Test
    void partitionCacheStillAcceptsNullRegions() {
        assertNotNull(AwsRulesFunction.AWS_PARTITION.apply1("us-east-1"));
        assertNull(AwsRulesFunction.AWS_PARTITION.apply1(null));
        assertNull(AwsRulesFunction.AWS_PARTITION.apply1(null));
    }
}
