/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;

// TODO: What package should this go under?
/**
 * Sets a Region name for a client to use.
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
public interface RegionSetting {
    /**
     * Region
     */
    Context.Key<String> REGION = Context.key("Region name. For example `us-east-2`");

    @Configuration(description = "Set the region to use.")
    default void region(Context context, String region) {
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("Region name cannot be null or empty.");
        }
        context.put(REGION, region);
    }
}
