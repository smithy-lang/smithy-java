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
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Injects a default idempotency token into the input if it's modeled but missing.
 */
public final class InjectIdempotencyTokenPlugin implements ClientPlugin {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(InjectIdempotencyTokenPlugin.class);
    private static final ClientInterceptor INTERCEPTOR = new Injector();

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
                String value = hook.input().getMemberValue(tokenMember);

                // Treat an empty string, possibly from error correction, as not present and set a default.
                if (value != null && value.isEmpty()) {
                    value = null;
                }

                if (value == null) {
                    var builder = operation.inputBuilder();
                    SchemaUtils.copyShape(hook.input(), builder);
                    builder.setMemberValue(tokenMember, UUID.randomUUID().toString());
                    LOGGER.debug("Injecting idempotency token into {} input", operation.schema().id());
                    return builder.build();
                }
            }

            return hook.input();
        }
    }
}
