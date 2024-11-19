/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.integrations.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.example.model.AddBeerInput;
import software.amazon.smithy.java.example.model.AddBeerOutput;
import software.amazon.smithy.java.example.model.Beer;
import software.amazon.smithy.java.example.model.GetBeerInput;
import software.amazon.smithy.java.example.model.GetBeerOutput;

class LambdaEndpointTest {

    private static final LambdaEndpoint endpoint = new LambdaEndpoint();
    private static final Rpcv2CborCodec codec = Rpcv2CborCodec.builder().build();

    @Test
    public void canAddBeer() {
        String body = """
            {
                "beer": {
                    "name": "Oatmeal Stout",
                    "quanity": 1
                }
            }
            """;

        ProxyRequest request = ProxyRequest.builder()
            .httpMethod("POST")
            .path("/add-beer")
            .requestContext(
                RequestContext.builder()
                    .requestId("abc123")
                    .build()
            )
            .body(body)
            .build();

        ProxyResponse response = endpoint.handleRequest(request, null);
        String expectedBody = "{\"id\":\"T2F0bWVhbCBTdG91dA==\"}";

        assertEquals(200, response.getStatusCode());
        assertEquals(expectedBody, response.getBody());
    }

    @Test
    public void canGetBeer() {
        String body = """
            {
                "id": "TXVuaWNoIEhlbGxlcw=="
            }
            """;

        ProxyRequest request = ProxyRequest.builder()
            .httpMethod("POST")
            .path("/get-beer")
            .requestContext(
                RequestContext.builder()
                    .requestId("abc123")
                    .build()
            )
            .body(body)
            .build();

        ProxyResponse response = endpoint.handleRequest(request, null);
        String expectedBody = "{\"beer\":{\"name\":\"Munich Helles\",\"quantity\":1}}";

        assertEquals(200, response.getStatusCode());
        assertEquals(expectedBody, response.getBody());
    }

    @Test
    public void canAddBeer_cbor() {
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

        ProxyResponse response = endpoint.handleRequest(request, null);

        AddBeerOutput output = getOutput(response.getBody(), AddBeerOutput.builder());
        AddBeerOutput expectedOutput = AddBeerOutput.builder()
            .id("T2F0bWVhbCBTdG91dA==")
            .build();

        assertEquals(200, response.getStatusCode());
        assertEquals(expectedOutput, output);
    }

    @Test
    public void canGetBeer_cbor() {
        GetBeerInput input = GetBeerInput.builder()
            .id("TXVuaWNoIEhlbGxlcw==")
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

        ProxyResponse response = endpoint.handleRequest(request, null);

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

    private static String getBody(SerializableStruct inputShape) {
        ByteArrayOutputStream in = new ByteArrayOutputStream();
        ShapeSerializer serializer = codec.createSerializer(in);
        inputShape.serialize(serializer);

        return Base64.getEncoder().encodeToString(in.toByteArray());
    }

    private static <T extends SerializableShape> T getOutput(String output, ShapeBuilder<T> outputShapeBuilder) {
        ShapeDeserializer deserializer = codec.createDeserializer(Base64.getDecoder().decode(output));
        return outputShapeBuilder.deserialize(deserializer).build();
    }
}
