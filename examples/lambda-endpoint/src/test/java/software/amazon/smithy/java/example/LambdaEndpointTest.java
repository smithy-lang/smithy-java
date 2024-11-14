/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.integrations.lambda.ProxyRequest;
import software.amazon.smithy.java.aws.integrations.lambda.ProxyResponse;
import software.amazon.smithy.java.aws.integrations.lambda.RequestContext;
import software.amazon.smithy.java.example.model.*;
import software.amazon.smithy.java.runtime.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

class LambdaEndpointTest {

    private static final LambdaMain handler = new LambdaMain();

    private static final Rpcv2CborCodec codec = Rpcv2CborCodec.builder().build();

    static String getBody(SerializableStruct inputShape) {
        ByteArrayOutputStream in = new ByteArrayOutputStream();
        ShapeSerializer serializer = codec.createSerializer(in);
        inputShape.serialize(serializer);

        return Base64.getEncoder().encodeToString(in.toByteArray());
    }

    static <T extends SerializableShape> T getOutput(String output, ShapeBuilder<T> outputShapeBuilder) {
        ShapeDeserializer deserializer = codec.createDeserializer(Base64.getDecoder().decode(output));
        return outputShapeBuilder.deserialize(deserializer).build();
    }

    @Test
    public void canAddBeer() {
        AddBeerInput input = AddBeerInput.builder()
            .beer(
                Beer.builder()
                    .name("Oatmeal Stout")
                    .quantity(1)
                    .build()
            )
            .build();

        ProxyRequest request = ProxyRequest.builder()
            .httpMethod("POST")
            .path("/service/BeerService/operation/AddBeer")
            .multiValueHeaders(
                Map.of(
                    "smithy-protocol",
                    List.of("rpc-v2-cbor"),
                    "content-type",
                    List.of("application/cbor")
                )
            )
            .requestContext(
                RequestContext.builder()
                    .requestId("abc123")
                    .build()
            )
            .isBase64Encoded(true)
            .body(getBody(input))
            .build();

        ProxyResponse response = handler.handleRequest(request, null);

        AddBeerOutput output = getOutput(response.getBody(), AddBeerOutput.builder());
        AddBeerOutput expectedOutput = AddBeerOutput.builder()
            .id(2)
            .build();

        assertEquals(200, response.getStatusCode());
        assertEquals(expectedOutput, output);
    }

    @Test
    public void canGetBeer() {
        GetBeerInput input = GetBeerInput.builder()
            .id(1)
            .build();

        ProxyRequest request = ProxyRequest.builder()
            .httpMethod("POST")
            .path("/service/BeerService/operation/GetBeer")
            .multiValueHeaders(
                Map.of(
                    "smithy-protocol",
                    List.of("rpc-v2-cbor"),
                    "content-type",
                    List.of("application/cbor")
                )
            )
            .requestContext(
                RequestContext.builder()
                    .requestId("abc123")
                    .build()
            )
            .isBase64Encoded(true)
            .body(getBody(input))
            .build();

        ProxyResponse response = handler.handleRequest(request, null);

        GetBeerOutput output = getOutput(response.getBody(), GetBeerOutput.builder());
        GetBeerOutput expectedOutput = GetBeerOutput.builder()
            .beer(
                Beer.builder()
                    .name("Munich Helles")
                    .quantity(1)
                    .build()
            )
            .build();

        assertEquals(200, response.getStatusCode());
        assertEquals(expectedOutput, output);
    }
}
