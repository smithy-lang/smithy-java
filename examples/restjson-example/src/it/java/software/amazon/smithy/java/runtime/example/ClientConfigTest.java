/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointResolver;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointResolverParams;
import software.amazon.smithy.java.runtime.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.example.model.GetPersonImage;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPerson;
import software.amazon.smithy.java.runtime.example.model.PutPersonImage;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

public class ClientConfigTest {

    @Test
    public void vanillaClient() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("http://httpbin.org/anything")
            .build();

        callOperation(client);
    }

    @Test
    public void clientWithDefaults() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .build();

        callOperation(client);
    }

    @Test
    public void clientWithDefaultsOverridden() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .endpoint("http://httpbin.org/anything")
            .build();

        callOperation(client);
    }

    private static final class PersonDirectoryClientWithDefaults extends Client implements PersonDirectoryClient {
        public PersonDirectoryClientWithDefaults(PersonDirectoryClientWithDefaults.Builder builder) {
            super(builder);

        }

        @Override
        public GetPersonImageOutput getPersonImage(GetPersonImageInput input, Context context) {
            return call(input, new GetPersonImage(), context).join();
        }

        @Override
        public PutPersonOutput putPerson(PutPersonInput input, Context context) {
            return call(input, new PutPerson(), context).join();
        }

        @Override
        public PutPersonImageOutput putPersonImage(PutPersonImageInput input, Context context) {
            return call(input, new PutPersonImage(), context).join();
        }

        static PersonDirectoryClientWithDefaults.Builder builder() {
            return new PersonDirectoryClientWithDefaults.Builder();
        }

        static final class Builder extends
            Client.Builder<PersonDirectoryClient, PersonDirectoryClientWithDefaults.Builder> {

            private Builder() {
                configBuilder.protocol(new RestJsonClientProtocol());
                configBuilder.transport(new JavaHttpClientTransport(HttpClient.newHttpClient()));

                List<ClientPlugin> defaultPlugins = List.of(new RandomEndpointPlugin());
                // Default plugins are "applied" here in Builder constructor.
                // They are not affected by any configuration added to Client.Builder.
                // Only things available in configBuilder to these default plugins would be things added to
                // configBuilder above.
                for (ClientPlugin plugin : defaultPlugins) {
                    plugin.configureClient(configBuilder);
                }
            }

            @Override
            public PersonDirectoryClient build() {
                return new PersonDirectoryClientWithDefaults(this);
            }
        }

        static final class RandomEndpointPlugin implements ClientPlugin {
            @Override
            public void configureClient(ClientConfig.Builder config) {
                config.endpointResolver(new RandomEndpointResolver());
            }
        }

        static final class RandomEndpointResolver implements EndpointResolver {
            private static final Random RANDOM = new Random();

            @Override
            public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
                int bound = 32;
                return CompletableFuture.completedFuture(
                    Endpoint.builder()
                        .uri("http://httpbin.org/anything/random-" + RANDOM.nextInt(bound))
                        .build()
                );
            }
        }
    }

    private static void callOperation(PersonDirectoryClient client) {
        PutPersonInput input = PutPersonInput.builder()
            .name("Michael")
            .age(999)
            .favoriteColor("Green")
            .birthday(Instant.now())
            .build();

        PutPersonOutput output = client.putPerson(input);
    }

    @Test
    public void supportsInterceptors() throws Exception {
        var interceptor = new ClientInterceptor() {
            @Override
            public void readBeforeTransmit(RequestHook<?, ?> hook) {
                System.out.println("Sending request: " + hook.input());
            }

            @Override
            public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, RequestT> hook) {
                return hook.mapRequest(SmithyHttpRequest.class, request -> {
                    return request.withAddedHeaders("X-Foo", "Bar");
                });
            }
        };

        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("http://httpbin.org/anything")
            .addInterceptor(interceptor)
            .build();

        callOperation(client);
    }
}
