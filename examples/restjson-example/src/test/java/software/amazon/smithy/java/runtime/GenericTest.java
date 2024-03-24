/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.api.EndpointProvider;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.CallContext;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.http.JavaHttpClient;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.java.runtime.example.PersonDirectoryClient;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PersonDirectory;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;
import software.amazon.smithy.java.runtime.example.model.ValidationError;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.json.JsonCodec;

public class GenericTest {

    @Test
    public void putPerson() {
        HttpClient httpClient = HttpClient.newBuilder().build();
        PersonDirectory client = PersonDirectoryClient.builder()
                .protocol(new RestJsonClientProtocol(new JavaHttpClient(httpClient)))
                .endpointProvider(EndpointProvider.staticEndpoint("https://httpbin.org/anything"))
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
    public void getPersonImage() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder().build();
        PersonDirectory client = PersonDirectoryClient.builder()
                .protocol(new RestJsonClientProtocol(new JavaHttpClient(httpClient)))
                .endpointProvider(EndpointProvider.staticEndpoint("https://httpbin.org"))
                .build();
        GetPersonImageInput input = GetPersonImageInput.builder().name("Michael").build();
        GetPersonImageOutput output = client.getPersonImage(input);
        try (InputStream is = output.image().inputStream()) {
            System.out.println(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void streamingRequestPayload() {
        HttpClient httpClient = HttpClient.newBuilder().build();
        PersonDirectory client = PersonDirectoryClient.builder()
                .protocol(new RestJsonClientProtocol(new JavaHttpClient(httpClient)))
                .endpointProvider(EndpointProvider.staticEndpoint("https://example.com"))
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
    public void testAny() {
        Codec codec = JsonCodec.builder()
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();

        PutPersonInput input = PutPersonInput.builder()
                .name("Michael")
                .age(999)
                .favoriteColor("Green")
                .birthday(Instant.now())
                .binary("Hello".getBytes(StandardCharsets.UTF_8))
                .build();

        // Serialize directly to JSON.
        System.out.println(codec.serializeToString(input));

        // Convert to an Any and then serialize to JSON.
        Any any = Any.of(input);
        System.out.println(codec.serializeToString(any));

        // Send the Any to a person builder.
        PutPersonInput inputCopy = any.asShape(PutPersonInput.builder());

        // Now serialize that to see that it round-trips.
        System.out.println(codec.serializeToString(inputCopy));
    }

    @Test
    public void testTypeRegistry() {
        TypeRegistry registry = TypeRegistry.builder()
                .putType(PutPersonInput.ID, PutPersonInput.class, PutPersonInput::builder)
                .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
                .build();

        registry.create(ValidationError.ID, ModeledSdkException.class)
                .map(SdkShapeBuilder::build)
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
            public void readBeforeTransmit(Context context) {
                System.out.println("Calling with: " + context.get(CallContext.INPUT));
            }

            @Override
            public <T> T modifyRequestBeforeTransmit(Context context, T request) {
                if (request instanceof SmithyHttpRequest r) {
                    return (T) r.withHeaders("X-foo", "Bar"); // TODO
                }
                return request;
            }
        };

        HttpClient httpClient = HttpClient.newBuilder().build();
        PersonDirectory client = PersonDirectoryClient.builder()
                .protocol(new RestJsonClientProtocol(new JavaHttpClient(httpClient)))
                .endpointProvider(EndpointProvider.staticEndpoint("https://httpbin.org"))
                .interceptor(interceptor)
                .build();

        GetPersonImageInput input = GetPersonImageInput.builder().name("Michael").build();
        GetPersonImageOutput output = client.getPersonImage(input);
        System.out.println(output.image().readToString(1000));
    }
}
