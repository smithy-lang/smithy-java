/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.auth.scheme.sigv4;

import software.amazon.smithy.java.aws.runtime.client.core.settings.RegionSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.settings.ClockSetting;

/**
 * Configuration properties used by clients for configuration related to SigV4.
 *
 * <p>These properties should be set in the client configuration by a plugin (default or manually applied) as opposed
 * to {@code AuthProperties} that are set by the AuthScheme. The {@link SigV4AuthScheme} use the follow properties:
 * <dl>
 *     <dt>region (<strong>REQUIRED</strong>)</dt>
 *     <dd>Region name to use for generating signatures.</dd>
 *     <dt>signingName (optional)</dt>
 *     <dd>The signing name for the service to use when signing a request. If no signingName is provided, then the
 *     signing name set on the {@code @sigv4} trait (if using the auth factory) or used to instantiate the AuthScheme
 *     will be used.
 *     </dd>
 *     <dt>clock (optional)</dt>
 *     <dd>Clock to use to determine the signing instant. If no clock setting is provided then the default system
 *     utc clock is used.
 *     </dd>
 * </dl>
 *
 * @implNote To apply this Setting to a client, add the setting to a client plugin that will be applied as a default
 * plugin to your client. For example:
 * <pre>{@code
 *     public final MyDefaultPlugin implements ClientPlugin, SigV4Settings {
 *
 *     @Override
 *     public void configureClient(ClientConfig.Builder config) {
 *         // Additional configuration
 *     }
 * }
 * }</pre>
 */
public interface SigV4Settings<B extends Client.Builder<?, B>> extends ClockSetting<B>, RegionSetting<B> {
    /**
     * Service name to use for signing. For example {@code lambda}.
     */
    Context.Key<String> SIGNING_NAME = Context.key("Signing name to use for computing SigV4 signatures.");

    /**
     * Signing name to use for the SigV4 signing process.
     *
     * <p>The signing name is typically the name of the service. For example {@code "lambda"}.
     *
     * @param signingName signing name.
     */
    default B signingName(String signingName) {
        if (signingName == null || signingName.isEmpty()) {
            throw new IllegalArgumentException("signingName cannot be null or empty");
        }
        return putConfig(SIGNING_NAME, signingName);
    }
}
