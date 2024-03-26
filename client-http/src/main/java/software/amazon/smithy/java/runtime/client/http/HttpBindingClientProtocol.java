/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.net.URI;
import software.amazon.smithy.java.runtime.client.core.ClientCall;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.http.api.HttpClientCall;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpClient;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.binding.HttpBinding;

/**
 * An HTTP-based protocol that uses HTTP binding traits.
 */
public class HttpBindingClientProtocol extends HttpClientProtocol {

    public HttpBindingClientProtocol(String id, SmithyHttpClient client, Codec codec) {
        super(id, client, codec);
    }

    @Override
    protected SmithyHttpRequest createHttpRequest(ClientCall<?, ?> call, Codec codec, URI endpoint) {
        return HttpBinding.requestSerializer()
                .operation(call.operation().schema())
                .payload(call.requestInputStream().orElse(null))
                .payloadCodec(codec)
                .shapeValue(call.input())
                .endpoint(endpoint)
                .serializeRequest();
    }

    @Override
    protected <I extends SerializableShape, O extends SerializableShape> void deserializeHttpResponse(
            ClientCall<I, O> call,
            Codec codec,
            SmithyHttpRequest request,
            SmithyHttpResponse response,
            SdkShapeBuilder<O> builder) {
        HttpBinding.responseDeserializer()
                .payloadCodec(codec)
                .outputShapeBuilder(builder)
                .response(response)
                .deserialize();
    }

    @Override
    SmithyHttpResponse sendHttpRequest(ClientCall<?, ?> call, SmithyHttpClient client, SmithyHttpRequest request) {
        return client.send(HttpClientCall.builder()
                .request(request)
                .properties(call.context().get(HttpContext.HTTP_PROPERTIES))
                .build());
    }
}
