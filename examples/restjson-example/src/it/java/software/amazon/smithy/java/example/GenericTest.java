/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example;

import static java.nio.ByteBuffer.wrap;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.example.client.PersonDirectoryClient;
import software.amazon.smithy.java.example.model.GetPersonImageInput;
import software.amazon.smithy.java.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.example.model.PutPersonImageInput;
import software.amazon.smithy.java.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.example.model.PutPersonInput;
import software.amazon.smithy.java.example.model.PutPersonOutput;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonCodec;

public class GenericTest {

    @Test
    public void putPerson() throws ExecutionException, InterruptedException {
        // Create a generated client using rest-json and a fixed endpoint.
        var client = PersonDirectoryClient.builder()
            .endpointResolver(EndpointResolver.staticHost("http://httpbin.org/anything"))
            .build();

        PutPersonInput input = PutPersonInput.builder()
            .name("Michael")
            .age(999)
            .favoriteColor("Green")
            .birthday(Instant.now())
            .build();

        PutPersonOutput output = client.putPerson(input);
        System.out.println("Output: " + output);
    }

    @Test
    public void getPersonImage() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .endpointResolver(EndpointResolver.staticHost("http://httpbin.org/anything"))
            .build();

        GetPersonImageInput input = GetPersonImageInput.builder().name("Michael").build();
        GetPersonImageOutput output = client.getPersonImage(input);
        System.out.println("Output: " + output);
    }

    @Test
    public void streamingRequestPayload() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .endpointResolver(EndpointResolver.staticHost("http://httpbin.org/anything"))
            .build();

        PutPersonImageInput input = PutPersonImageInput.builder()
            .name("Michael")
            .tags(List.of("Foo", "Bar"))
            .moreTags(List.of("Abc", "one two"))
            .image(DataStream.ofString("image..."))
            .build();
        PutPersonImageOutput output = client.putPersonImage(input);
        System.out.println("Output: " + output);
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
            public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                System.out.println("Sending request: " + hook.input());
            }

            @Override
            public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
                return hook.mapRequest(
                    HttpRequest.class,
                    h -> h.request().toBuilder().withAddedHeader("X-Foo", "Bar").build()
                );
            }
        };

        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .endpointResolver(EndpointResolver.staticHost("http://httpbin.org/anything"))
            .addInterceptor(interceptor)
            .build();

        GetPersonImageInput input = GetPersonImageInput.builder().name("Michael").build();
        GetPersonImageOutput output = client.getPersonImage(input);
        System.out.println("Output: " + output);
    }
}
