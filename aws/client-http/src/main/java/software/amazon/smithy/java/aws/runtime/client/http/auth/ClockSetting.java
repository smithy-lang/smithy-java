/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth;

import java.time.Clock;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;

// TODO: What package should this go under?
/**
 * Setting allowing users to override the {@link Clock} used by clients.
 *
 * @implNote This setting can be implemented directly by a client plugin, but is most
 * often implemented by other settings to create aggregate settings for a feature. For example:
 * <pre>{@code
 * public interface SettingRequiredForAFeature implements Clock, ETC {
 *     ...
 * }
 * }</pre>
 */
public interface ClockSetting {
    /**
     * Override the {@code Clock} implementation to used in clients.
     */
    Context.Key<Clock> CLOCK = Context.key("Clock override.");

    @Configuration(description = "Override the default client clock.")
    default void clock(Context context, Clock clock) {
        context.put(CLOCK, Objects.requireNonNull(clock, "clock cannot be null"));
    }
}
