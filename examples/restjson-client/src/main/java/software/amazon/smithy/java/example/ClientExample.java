package software.amazon.smithy.java.example;

import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.example.restjson.client.PersonDirectoryClient;
import software.amazon.smithy.java.example.restjson.model.GetPersonImageInput;
import software.amazon.smithy.java.example.restjson.model.GetPersonImageOutput;
import software.amazon.smithy.java.example.restjson.model.PutPersonImageInput;
import software.amazon.smithy.java.example.restjson.model.PutPersonImageOutput;
import software.amazon.smithy.java.example.restjson.model.PutPersonInput;
import software.amazon.smithy.java.example.restjson.model.PutPersonOutput;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonCodec;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static java.nio.ByteBuffer.wrap;

public final class ClientExample {
    public static void main(String[] args) throws Exception {
        putPerson();
        getPersonImage();
        streamingRequestPayload();
        testDocument();
        serde();
        supportsInterceptors();
    }

    public static void putPerson() throws ExecutionException, InterruptedException {
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

    public static void getPersonImage() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .endpointResolver(EndpointResolver.staticHost("http://httpbin.org/anything"))
            .build();

        GetPersonImageInput input = GetPersonImageInput.builder().name("Michael").build();
        GetPersonImageOutput output = client.getPersonImage(input);
        System.out.println("Output: " + output);
    }

    public static void streamingRequestPayload() {
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

    public static void testDocument() {
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
        Document document = Document.of(input);
        System.out.println(codec.serializeToString(document));

        // Send the Document to a person builder.
        PutPersonInput inputCopy = document.asShape(PutPersonInput.builder());

        // Now serialize that to see that it round-trips.
        System.out.println(codec.serializeToString(inputCopy));
    }

    public static void serde() {
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

    public static void supportsInterceptors() throws Exception {
        var interceptor = new ClientInterceptor() {
            @Override
            public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                System.out.println("Sending request: " + hook.input());
            }

            @Override
            public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
                return hook.mapRequest(
                    HttpRequest.class,
                    h -> h.request().toBuilder().withAddedHeader("X-Foo", "Bar").build());
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
