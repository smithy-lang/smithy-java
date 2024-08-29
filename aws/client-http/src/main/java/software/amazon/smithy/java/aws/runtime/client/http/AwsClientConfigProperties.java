/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http;

import software.amazon.smithy.java.context.Context;

/**
 * Configuration properties used by all AWS Service clients for configuration.
 */
public final class AwsClientConfigProperties {
    public static final Context.Key<String> REGION = Context.key("region");
}
