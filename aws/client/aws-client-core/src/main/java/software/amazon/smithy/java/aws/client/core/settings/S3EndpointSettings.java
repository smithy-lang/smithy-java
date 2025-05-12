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
public interface S3EndpointSettings<B extends ClientSetting<B>> extends EndpointSettings<B> {
    /**
     * If the SDK client is configured to use S3 transfer acceleration, defaults to false.
     */
    Context.Key<Boolean> S3_ACCELERATE = Context.key("AWS::S3::Accelerate");

    /**
     * If the SDK client is configured to not use S3's multi-region access points, defaults to false.
     */
    Context.Key<Boolean> S3_DISABLE_MULTI_REGION_ACCESS_POINTS = Context.key("AWS::S3::DisableMultiRegionAccessPoints");

    /**
     * If the SDK client is configured to use solely S3 path style routing, defaults to false.
     */
    Context.Key<Boolean> S3_FORCE_PATH_STYLE = Context.key("AWS::S3::ForcePathStyle");

    /**
     * If the SDK client is configured to use S3 bucket ARN regions or raise an error when the bucket ARN and client
     * region differ, defaults to true.
     */
    Context.Key<Boolean> S3_USE_ARN_REGION = Context.key("AWS::S3::UseArnRegion");

    /**
     * If the SDK client is configured to use S3's global endpoint instead of the regional us-east-1 endpoint,
     * defaults to false.
     */
    Context.Key<Boolean> S3_USE_GLOBAL_ENDPOINT = Context.key("AWS::S3::UseGlobalEndpoint");

    /**
     * If the SDK client is configured to use S3 Control bucket ARN regions or raise an error when the bucket ARN
     * and client region differ, defaults to true.
     */
    Context.Key<Boolean> S3_CONTROL_USE_ARN_REGION = Context.key("AWS::S3Control::UseArnRegion");

    /**
     * Configures if the SDK client is configured to use S3 transfer acceleration, defaults to false.
     *
     * @param useS3Accelerate True to enable.
     * @return self
     */
    default B s3useAccelerate(boolean useS3Accelerate) {
        return putConfig(S3_ACCELERATE, useS3Accelerate);
    }

    /**
     * If the SDK client is configured to not use S3's multi-region access points, defaults to false.
     *
     * @param value True to disable MRAP.
     * @return self
     */
    default B s3disableMultiRegionAccessPoints(boolean value) {
        return putConfig(S3_DISABLE_MULTI_REGION_ACCESS_POINTS, value);
    }

    /**
     * If the SDK client is configured to use solely S3 path style routing, defaults to false.
     *
     * @param usePathStyle True to force path style.
     * @return self
     */
    default B s3forcePathStyle(boolean usePathStyle) {
        return putConfig(S3_FORCE_PATH_STYLE, usePathStyle);
    }

    /**
     * If the SDK client is configured to use S3 bucket ARN regions or raise an error when the bucket ARN and client
     * region differ, defaults to true.
     *
     * @param useArnRegion True to use ARN region.
     * @return self
     */
    default B s3useArnRegion(boolean useArnRegion) {
        return putConfig(S3_USE_ARN_REGION, useArnRegion);
    }

    /**
     * If the SDK client is configured to use S3's global endpoint instead of the regional us-east-1 endpoint,
     * defaults to false.
     *
     * @param useGlobalEndpoint True to enable global endpoint.
     * @return self
     */
    default B s3useGlobalEndpoint(boolean useGlobalEndpoint) {
        return putConfig(S3_USE_GLOBAL_ENDPOINT, useGlobalEndpoint);
    }

    /**
     * If the SDK client is configured to use S3 Control bucket ARN regions or raise an error when the bucket ARN
     * and client region differ, defaults to true.
     *
     * @param useArnRegion True to enable S3 control ARN region.
     * @return self
     */
    default B s3controlUseArnRegion(boolean useArnRegion) {
        return putConfig(S3_CONTROL_USE_ARN_REGION, useArnRegion);
    }
}
