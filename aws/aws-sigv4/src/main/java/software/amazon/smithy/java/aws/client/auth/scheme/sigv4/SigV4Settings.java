/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.client.core.settings.ClockSetting;
import software.amazon.smithy.java.context.Context;

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
public interface SigV4Settings<B extends ClientSetting<B>> extends ClockSetting<B>, RegionSetting<B> {
    /**
     * Service name to use for signing. For example {@code lambda}.
     */
    Context.Key<String> SIGNING_NAME = Context.key("Signing name to use for computing SigV4 signatures.");

    /**
     * If set, this string is used verbatim as the body-hash component of the SigV4 canonical
     * request and as the value of the {@code x-amz-content-sha256} header — bypassing the SHA-256
     * computation over the request body.
     *
     * <p>Used by callers that frame the body in {@code aws-chunked} form and emit a checksum
     * trailer instead of including a body SHA-256 in the signature. The expected values come
     * from the AWS sigv4-streaming spec, e.g. {@code "STREAMING-UNSIGNED-PAYLOAD-TRAILER"} or
     * {@code "UNSIGNED-PAYLOAD"}.
     *
     * <p>The setter is responsible for replacing the request body with chunked-encoded bytes and
     * adjusting {@code Content-Length} / {@code Content-Encoding} accordingly. The signer just
     * stamps the override string and trusts the caller's framing.
     */
    Context.Key<String> PAYLOAD_HASH_OVERRIDE = Context.key("SigV4 body-hash override (e.g. STREAMING-UNSIGNED-PAYLOAD-TRAILER).");

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
