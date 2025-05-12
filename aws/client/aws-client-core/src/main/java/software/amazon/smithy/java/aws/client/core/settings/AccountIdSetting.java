/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.settings;

import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

/**
 * Configures an AWS Account ID.
 */
public interface AccountIdSetting<B extends ClientSetting<B>> extends RegionSetting<B> {
    /**
     * AWS Account ID to use.
     */
    Context.Key<String> AWS_ACCOUNT_ID = Context.key("AWS Account ID");

    /**
     * Sets the AWS Account ID.
     *
     * @param awsAccountId AWS account ID to set.
     * @return self
     */
    default B awsAccountId(String awsAccountId) {
        return putConfig(AWS_ACCOUNT_ID, awsAccountId);
    }
}
