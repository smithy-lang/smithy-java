/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.settings;

import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

/**
 * Sets the AWS Region name for a client to use.
 *
 * <p>Each region represents is a separate geographic area.
 *
 * @implNote This setting can be implemented directly by a client plugin, but is most
 * often implemented by other settings to create aggregate settings for a feature. For example:
 * <pre>{@code
 * public interface SettingRequiredForAFeature implements Region, ETC {
 *     ...
 * }
 * }</pre>
 */
public interface RegionSetting<B extends ClientSetting<B>> extends ClientSetting<B> {
    /**
     * AWS Region.
     *
     * @see <a href="https://docs.aws.amazon.com/general/latest/gr/rande.html">AWS service endpoints</a>
     */
    Context.Key<String> REGION = Context.key("Region name. For example `us-east-2`");

    /**
     * Set the AWS region for a client to use.
     *
     * @param region AWS region.
     */
    default void region(String region) {
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("Region name cannot be null or empty.");
        }
        putConfig(REGION, region);
    }
}
