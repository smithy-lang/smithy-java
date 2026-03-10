/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import java.util.UUID;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeUtils;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Injects a default idempotency token into the input if it's modeled but missing.
 *
 * <p>This plugin is added to clients by default via {@link DefaultPlugin}.
 */
@SmithyInternalApi
public final class InjectIdempotencyTokenPlugin implements ClientPlugin {

    public static final InjectIdempotencyTokenPlugin INSTANCE = new InjectIdempotencyTokenPlugin();
    public static final Context.Key<String> IDEMPOTENCY_TOKEN_PROVIDER = Context.key("IdempotencyTokenProvider");

    private static final InternalLogger LOGGER = InternalLogger.getLogger(InjectIdempotencyTokenPlugin.class);
    private static final ClientInterceptor INTERCEPTOR = new Injector();

    private InjectIdempotencyTokenPlugin() {}

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.addInterceptor(INTERCEPTOR);
    }

    private static final class Injector implements ClientInterceptor {
        @Override
        public <I extends SerializableStruct> I modifyBeforeSerialization(InputHook<I, ?> hook) {
            var operation = hook.operation();
            var tokenMember = operation.idempotencyTokenMember();

            if (tokenMember != null) {
                var value = hook.input().getMemberValue(tokenMember);

                // Treat an empty string, possibly from error correction, as not present and set a default.
                if (value instanceof String s && s.isEmpty()) {
                    value = null;
                }

                if (value == null) {
                    var builder = operation.inputBuilder();
                    ShapeUtils.copyShape(hook.input(), builder);
                    var tokenProvider = hook.context().get(IDEMPOTENCY_TOKEN_PROVIDER);
                    var tokenValue = tokenProvider != null ? tokenProvider : UUID.randomUUID().toString();
                    builder.setMemberValue(tokenMember, tokenValue);
                    LOGGER.debug("Injecting idempotency token into {} input", operation.schema().id());
                    return builder.build();
                }
            }

            return hook.input();
        }
    }
}
