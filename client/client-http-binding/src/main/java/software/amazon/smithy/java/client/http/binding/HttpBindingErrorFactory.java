/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.binding;

import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.binding.HttpBinding;

/**
 * The default strategy that uses HTTP bindings to deserialize errors.
 */
public final class HttpBindingErrorFactory implements HttpErrorDeserializer.KnownErrorFactory {

    private final HttpBinding httpBinding;

    public HttpBindingErrorFactory() {
        this(new HttpBinding());
    }

    /**
     * @param httpBinding The HTTP binding cache to use for serde.
     */
    public HttpBindingErrorFactory(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

    @Override
    public ModeledException createError(
            Context context,
            Codec codec,
            HttpResponse response,
            ShapeBuilder<ModeledException> builder
    ) {
        httpBinding.responseDeserializer()
                .payloadCodec(codec)
                .errorShapeBuilder(builder)
                .response(response)
                .deserialize();
        return builder.errorCorrection().build();
    }
}
