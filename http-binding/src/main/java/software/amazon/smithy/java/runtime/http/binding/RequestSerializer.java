/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.net.URI;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.uri.URIBuilder;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 * Serializes an HTTP request from an input shape that uses HTTP binding traits.
 */
public final class RequestSerializer {

    private Codec payloadCodec;
    private SdkSchema operation;
    private URI endpoint;
    private SerializableShape shapeValue;
    private DataStream payload;
    private final BindingMatcher bindingMatcher = BindingMatcher.requestMatcher();

    RequestSerializer() {}

    /**
     * Schema of the operation to serialize.
     *
     * @param operation Operation schema.
     * @return Returns the serializer.
     */
    public RequestSerializer operation(SdkSchema operation) {
        if (operation.type() != ShapeType.OPERATION) {
            throw new IllegalArgumentException("operation must be an operation, but found " + operation);
        }
        this.operation = operation;
        return this;
    }

    /**
     * Codec to use in the payload of requests.
     *
     * @param payloadCodec Payload codec.
     * @return Returns the serializer.
     */
    public RequestSerializer payloadCodec(Codec payloadCodec) {
        this.payloadCodec = payloadCodec;
        return this;
    }

    /**
     * Set the endpoint of the request.
     *
     * @param endpoint Request endpoint.
     * @return Returns the serializer.
     */
    public RequestSerializer endpoint(URI endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Set the value of the request shape.
     *
     * @param shapeValue Request shape value to serialize.
     * @return Returns the serializer.
     */
    public RequestSerializer shapeValue(SerializableShape shapeValue) {
        this.shapeValue = shapeValue;
        return this;
    }

    /**
     * Set the streaming payload of the request, if any.
     *
     * @param payload Payload to associate to the request.
     * @return Returns the serializer.
     */
    public RequestSerializer payload(DataStream payload) {
        this.payload = payload;
        return this;
    }

    /**
     * Finishes setting up the serializer and creates an HTTP request.
     *
     * @return Returns the created request.
     */
    public SmithyHttpRequest serializeRequest() {
        Objects.requireNonNull(shapeValue, "shapeValue is not set");
        Objects.requireNonNull(operation, "operation is not set");
        Objects.requireNonNull(payloadCodec, "payloadCodec is not set");
        Objects.requireNonNull(payloadCodec, "endpoint is not set");
        Objects.requireNonNull(payloadCodec, "value is not set");

        var httpTrait = operation.expectTrait(HttpTrait.class);
        var serializer = new HttpBindingSerializer(httpTrait, payloadCodec, bindingMatcher, payload);
        shapeValue.serialize(serializer);
        serializer.flush();

        var uriBuilder = URIBuilder.of(endpoint);

        // Append the path using simple concatenation, not using RFC 3986 resolution.
        uriBuilder.concatPath(serializer.getPath());

        if (serializer.hasQueryString()) {
            uriBuilder.query(serializer.getQueryString());
        }

        var targetEndpoint = uriBuilder.build();

        return SmithyHttpRequest.builder()
            .method(httpTrait.getMethod())
            .uri(targetEndpoint)
            .headers(serializer.getHeaders())
            .body(serializer.getBody())
            .build();
    }
}
