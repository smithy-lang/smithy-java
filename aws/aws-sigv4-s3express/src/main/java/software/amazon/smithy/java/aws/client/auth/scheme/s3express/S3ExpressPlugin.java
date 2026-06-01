/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.util.ServiceLoader;
import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;

/**
 * Wires up everything an S3 client needs to use the {@code aws.auth#sigv4S3express} auth scheme.
 *
 * <p>Registers, in {@link Phase#DEFAULTS}:
 *
 * <ul>
 *     <li>{@link S3ExpressAuthScheme} — but only when a {@link CreateSessionCallback} has been
 *         registered on the client config under
 *         {@link S3ExpressContext#CREATE_SESSION_CALLBACK}. The plugin does not own the
 *         callback construction because the callback typically closes over the generated
 *         S3 client itself (chicken-and-egg).</li>
 *     <li>{@link S3ExpressBucketInterceptor} — schema-driven, schema-shape-agnostic; reads
 *         the {@code Bucket} member off any operation input.</li>
 *     <li>{@link S3ExpressChecksumInterceptor} — defaults to CRC32 for any S3 Express request
 *         whose operation declares the flexible-checksum trait and whose body is replayable.</li>
 *     <li>{@link S3VirtualHostStyleInterceptor} — strips the redundant bucket segment from
 *         the request path when the resolved endpoint already places it in the host.</li>
 *     <li>{@link S3DisableExpressSessionAuthResolver} — env / profile precedence for the
 *         {@code DisableS3ExpressSessionAuth} setting.</li>
 * </ul>
 *
 * <p>Idempotent — if {@link S3ExpressAuthScheme} is already registered, doesn't add it again.
 *
 * <p>Discovered via {@link ServiceLoader} as an {@link AutoClientPlugin}. Setting the
 * {@link S3ExpressContext#CREATE_SESSION_CALLBACK} on the client config is the opt-in signal:
 *
 * <pre>{@code
 * MyClient.builder()
 *     .putConfig(S3ExpressContext.CREATE_SESSION_CALLBACK, (bucket, baseCreds) -> ...)
 *     .build();
 * }</pre>
 */
public final class S3ExpressPlugin implements AutoClientPlugin {

    private static final int BUCKET_INTERCEPTOR = 1;
    private static final int CHECKSUM_INTERCEPTOR = 1 << 1;
    private static final int VIRTUAL_HOST_INTERCEPTOR = 1 << 2;

    @Override
    public Phase getPluginPhase() {
        return Phase.DEFAULTS;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        int registered = registeredInterceptors(config);
        if ((registered & BUCKET_INTERCEPTOR) == 0) {
            config.addInterceptor(S3ExpressBucketInterceptor.INSTANCE);
        }
        if ((registered & CHECKSUM_INTERCEPTOR) == 0) {
            config.addInterceptor(S3ExpressChecksumInterceptor.INSTANCE);
        }
        if ((registered & VIRTUAL_HOST_INTERCEPTOR) == 0) {
            config.addInterceptor(S3VirtualHostStyleInterceptor.INSTANCE);
        }
        S3DisableExpressSessionAuthResolver.INSTANCE.configureClient(config);

        var callback = config.context().get(S3ExpressContext.CREATE_SESSION_CALLBACK);
        if (callback != null && !alreadyHasS3ExpressScheme(config)) {
            config.putSupportedAuthSchemes(new S3ExpressAuthScheme(callback));
        }
    }

    private static int registeredInterceptors(ClientConfig.Builder config) {
        int result = 0;
        for (var i : config.interceptors()) {
            if (i == S3ExpressBucketInterceptor.INSTANCE) {
                result |= BUCKET_INTERCEPTOR;
            } else if (i == S3ExpressChecksumInterceptor.INSTANCE) {
                result |= CHECKSUM_INTERCEPTOR;
            } else if (i == S3VirtualHostStyleInterceptor.INSTANCE) {
                result |= VIRTUAL_HOST_INTERCEPTOR;
            }
        }
        return result;
    }

    private static boolean alreadyHasS3ExpressScheme(ClientConfig.Builder config) {
        for (AuthScheme<?, ?> scheme : config.supportedAuthSchemes()) {
            if (scheme.schemeId().equals(S3ExpressAuthScheme.SCHEME_ID)) {
                return true;
            }
        }
        return false;
    }
}
