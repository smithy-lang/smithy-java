/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientSetting;

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
public interface RegionSetting<B extends Client.Builder<?, B>> extends ClientSetting<B> {
    /**
     * Region
     */
    Context.Key<String> REGION = Context.key("Region name. For example `us-east-2`");

    /**
     * TODO: DOCS
     * @param region region
     */
    default void region(String region) {
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("Region name cannot be null or empty.");
        }
        putConfig(REGION, region);
    }
}
