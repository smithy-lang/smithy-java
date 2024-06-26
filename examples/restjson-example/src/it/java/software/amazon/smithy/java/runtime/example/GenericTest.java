/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import static java.nio.ByteBuffer.wrap;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.http.HttpContext;
import software.amazon.smithy.java.runtime.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;
import software.amazon.smithy.java.runtime.example.model.ValidationError;
import software.amazon.smithy.java.runtime.json.JsonCodec;

public class GenericTest {

    @Test
    public void putPerson() {
        // Create a generated client using rest-json and a fixed endpoint.
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("http://httpbin.org/anything")
            .build();

        PutPersonInput input = PutPersonInput.builder()
            .name("Michael")
            .age(999)
            .favoriteColor("Green")
            .birthday(Instant.now())
            .build();

        PutPersonOutput output = client.putPerson(input);
    }

    @Test
    public void getPersonImage() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("http://httpbin.org/anything")
            .build();

        GetPersonImageInput input = GetPersonImageInput.builder().name("Michael").build();
        GetPersonImageOutput output = client.getPersonImage(input);

        System.out.println(output.image().asString().toCompletableFuture().join());
    }

    @Test
    public void streamingRequestPayload() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("http://httpbin.org/anything")
            .build();

        PutPersonImageInput input = PutPersonImageInput.builder()
            .name("Michael")
            .tags(List.of("Foo", "Bar"))
            .moreTags(List.of("Abc", "one two"))
            .image(DataStream.ofString("image..."))
            .build();
        PutPersonImageOutput output = client.putPersonImage(input);
    }

    @Test
    public void testDocument() {
        Codec codec = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build();

        PutPersonInput input = PutPersonInput.builder()
            .name("Michael")
            .age(999)
            .favoriteColor("Green")
            .birthday(Instant.now())
            .binary(wrap("Hello".getBytes(StandardCharsets.UTF_8)))
            .build();

        // Serialize directly to JSON.
        System.out.println(codec.serializeToString(input));

        // Convert to a Document and then serialize to JSON.
        Document document = Document.createTyped(input);
        System.out.println(codec.serializeToString(document));

        // Send the Document to a person builder.
        PutPersonInput inputCopy = document.asShape(PutPersonInput.builder());

        // Now serialize that to see that it round-trips.
        System.out.println(codec.serializeToString(inputCopy));
    }

    @Test
    public void testTypeRegistry() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(PutPersonInput.ID, PutPersonInput.class, PutPersonInput::builder)
            .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
            .build();

        registry.create(ValidationError.ID, ModeledApiException.class)
            .map(ShapeBuilder::build)
            .ifPresent(System.out::println);
    }

    @Test
    public void serde() {
        PutPersonInput input = PutPersonInput.builder()
            .name("Michael")
            .age(999)
            .favoriteColor("Green")
            .birthday(Instant.now())
            .build();

        JsonCodec codec = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build();

        // Use a helper to serialize the shape into string.
        String jsonString = codec.serializeToString(input);

        // Use a helper to deserialize directly into a builder and create the shape.
        PutPersonInput copy = codec.deserializeShape(jsonString, PutPersonInput.builder());

        // Dump out the copy of the shape.
        System.out.println(codec.serializeToString(copy));
    }

    @Test
    public void supportsInterceptors() throws Exception {
        var interceptor = new ClientInterceptor() {
            @Override
            public <I extends SerializableStruct, RequestT> void readBeforeTransmit(
                Context context,
                I input,
                Context.Value<RequestT> request
            ) {
                System.out.println("Sending request: " + input);
            }

            @Override
            public <I extends SerializableStruct, RequestT> Context.Value<RequestT> modifyBeforeTransmit(
                Context context,
                I input,
                Context.Value<RequestT> request
            ) {
                return request.mapIf(HttpContext.HTTP_REQUEST, r -> r.withAddedHeaders("X-Foo", "Bar"));
            }
        };

        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("http://httpbin.org/anything")
            .addInterceptor(interceptor)
            .build();

        GetPersonImageInput input = GetPersonImageInput.builder().name("Michael").build();
        GetPersonImageOutput output = client.getPersonImage(input);
        System.out.println(output.image().asString().toCompletableFuture().join());
    }
}
