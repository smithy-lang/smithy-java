/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 * Serializes HTTP responses.
 */
public final class ResponseSerializer {
    private Codec payloadCodec;
    private SdkSchema operation;
    private SerializableShape shapeValue;
    private final BindingMatcher bindingMatcher = BindingMatcher.responseMatcher();
    private DataStream payload;

    ResponseSerializer() {
    }

    /**
     * Schema of the operation response to serialize.
     *
     * @param operation Operation schema.
     * @return Returns the serializer.
     */
    public ResponseSerializer operation(SdkSchema operation) {
        if (operation.type() != ShapeType.OPERATION) {
            throw new IllegalArgumentException("operation must be an operation, but found " + operation);
        }
        this.operation = operation;
        return this;
    }

    /**
     * Codec to use in the payload of the response.
     *
     * @param payloadCodec Payload codec.
     * @return Returns the serializer.
     */
    public ResponseSerializer payloadCodec(Codec payloadCodec) {
        this.payloadCodec = payloadCodec;
        return this;
    }

    /**
     * Set the value of the response shape.
     *
     * @param shapeValue Response shape value to serialize.
     * @return Returns the serializer.
     */
    public ResponseSerializer shapeValue(SerializableShape shapeValue) {
        this.shapeValue = shapeValue;
        return this;
    }

    /**
     * Set the streaming payload of the response, if any.
     *
     * @param payload Payload to associate to the response.
     * @return Returns the serializer.
     */
    public ResponseSerializer payload(DataStream payload) {
        this.payload = payload;
        return this;
    }

    /**
     * Finishes setting up the serializer and creates an HTTP response.
     *
     * @return Returns the created response.
     */
    public SmithyHttpResponse serializeResponse() {
        Objects.requireNonNull(shapeValue, "shapeValue is not set");
        Objects.requireNonNull(operation, "operation is not set");
        Objects.requireNonNull(payloadCodec, "payloadCodec is not set");
        Objects.requireNonNull(payloadCodec, "endpoint is not set");
        Objects.requireNonNull(payloadCodec, "value is not set");

        var httpTrait = operation.expectTrait(HttpTrait.class);
        var serializer = new HttpBindingSerializer(httpTrait, payloadCodec, bindingMatcher, payload);
        shapeValue.serialize(serializer);
        serializer.flush();

        return SmithyHttpResponse.builder()
            .statusCode(serializer.getResponseStatus())
            .headers(serializer.getHeaders())
            .body(serializer.getBody().inputStream())
            .build();
    }
}
