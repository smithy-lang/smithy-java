/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.settings;

import java.time.Clock;
import java.util.Objects;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

/**
 * Setting allowing users to override the {@link Clock} used by clients.
 *
 * <p>Custom clocks can be used for test or to allow adjustment for clock skew.
 *
 * @implNote This setting can be implemented directly by a client plugin, but is most
 * often implemented by other settings to create aggregate settings for a feature. For example:
 * <pre>{@code
 * public interface SettingRequiredForAFeature implements Clock, ETC {
 *     ...
 * }
 * }</pre>
 */
public interface ClockSetting<B extends ClientSetting<B>> extends ClientSetting<B> {
    /**
     * Override the {@code Clock} implementation to used in clients.
     */
    Context.Key<Clock> CLOCK = Context.key("Clock override.");

    /**
     * Override the default client clock.
     *
     * @param clock clock to override default with.
     */
    default B clock(Clock clock) {
        return putConfig(CLOCK, Objects.requireNonNull(clock, "clock cannot be null"));
    }
}
