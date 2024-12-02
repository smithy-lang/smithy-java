/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.core.schema.ApiException;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Adds model-based retry information to an exception.
 *
 * <p>This plugin is added to clients by default via {@link DefaultPlugin}.
 */
@SmithyInternalApi
public final class ApplyModelRetryInfoPlugin implements ClientPlugin {

    public static final ApplyModelRetryInfoPlugin INSTANCE = new ApplyModelRetryInfoPlugin();

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.addInterceptor(Interceptor.INSTANCE);
    }

    private static final class Interceptor implements ClientInterceptor {
        private static final Interceptor INSTANCE = new Interceptor();

        @Override
        public <O extends SerializableStruct> O modifyBeforeAttemptCompletion(
            OutputHook<?, O, ?, ?> hook,
            RuntimeException error
        ) {
            if (error instanceof ApiException ae && ae.isRetrySafe() == RetrySafety.MAYBE) {
                applyRetryInfoFromModel(hook.operation().schema(), ae);
            }
            return hook.forward(error);
        }
    }

    static void applyRetryInfoFromModel(Schema operationSchema, ApiException e) {
        // If the operation is readonly or idempotent, then it's safe to retry (other checks can disqualify later).
        var isRetryable = operationSchema.hasTrait(TraitKey.READ_ONLY_TRAIT)
            || operationSchema.hasTrait(TraitKey.IDEMPOTENT_TRAIT);

        if (isRetryable) {
            e.isRetrySafe(RetrySafety.YES);
        }

        // If the exception is modeled as retryable or a throttle, then use that information.
        if (e instanceof ModeledApiException mae) {
            var retryTrait = mae.schema().getTrait(TraitKey.RETRYABLE_TRAIT);
            if (retryTrait != null) {
                e.isRetrySafe(RetrySafety.YES);
                e.isThrottle(retryTrait.getThrottling());
            }
        }
    }
}
