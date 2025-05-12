/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.settings;

import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

/**
 * Configures AWS specific endpoint settings.
 */
public interface StsEndpointSettings<B extends ClientSetting<B>> extends EndpointSettings<B> {
    /**
     * If the SDK client is configured to use STS' global endpoint instead of the regional us-east-1 endpoint,
     * defaults to false.
     */
    Context.Key<Boolean> STS_USE_GLOBAL_ENDPOINT = Context.key("AWS::STS::UseGlobalEndpoint");

    /**
     * Configures if if the SDK client is configured to use STS' global endpoint instead of the regional us-east-1
     * endpoint, defaults to false.
     *
     * @param useGlobalEndpoint True to enable global endpoints.
     * @return self
     */
    default B stsUseGlobalEndpoint(boolean useGlobalEndpoint) {
        return putConfig(STS_USE_GLOBAL_ENDPOINT, useGlobalEndpoint);
    }
}
