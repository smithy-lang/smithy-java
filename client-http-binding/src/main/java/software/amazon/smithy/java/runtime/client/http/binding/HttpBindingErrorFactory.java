/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.binding;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.binding.HttpBinding;

/**
 * The default strategy that uses HTTP bindings to deserialize errors.
 */
public final class HttpBindingErrorFactory implements HttpErrorDeserializer.KnownErrorFactory {
    @Override
    public CompletableFuture<ModeledApiException> createError(
        Context context,
        Codec codec,
        SmithyHttpResponse response,
        ShapeBuilder<ModeledApiException> builder
    ) {
        return HttpBinding.responseDeserializer()
            .payloadCodec(codec)
            .errorShapeBuilder(builder)
            .response(response)
            .deserialize()
            .thenApply(ignore -> builder.errorCorrection().build());
    }
}
