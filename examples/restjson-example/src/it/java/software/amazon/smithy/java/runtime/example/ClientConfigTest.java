/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointProperty;
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

    private RequestCapturingInterceptor requestCapturingInterceptor;

    @BeforeEach
    public void setup() {
        requestCapturingInterceptor = new RequestCapturingInterceptor();
    }

    @Test
    public void vanillaClient() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .addInterceptor(requestCapturingInterceptor)
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("http://httpbin.org/anything")
            .build();
        callOperation(client);
        SmithyHttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().toString()).matches("http://httpbin.org/anything/persons/.*");
    }

    @Test
    public void clientWithDefaults() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .build();
        callOperation(client);
        SmithyHttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().toString()).matches("http://httpbin.org/anything/random-\\d/persons/.*");
    }

    @Test
    public void clientWithDefaults_EndpointResolverOverridden() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .endpoint("http://httpbin.org/anything")
            .build();
        callOperation(client);
        SmithyHttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().toString()).matches("http://httpbin.org/anything/persons/.*");
    }

    @Test
    public void clientWithDefaults_EndpointResolverConfigKeyOverridden() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .put(RandomEndpointPlugin.BASE, 100)
            .build();
        callOperation(client);
        SmithyHttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        // TODO: this won't as expected right now, because we aren't flowing Context BASE to EndpointResolver
//        assertThat(request.uri().toString()).matches("http://httpbin.org/anything/random-1\\d/persons/.*");
    }

    @Test
    public void clientWithDefaults_EndpointResolverConfigKeyOverridden_PluginReAdded() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .addPlugin(new RandomEndpointPlugin())
            .put(RandomEndpointPlugin.BASE, 100)
            .build();
        callOperation(client);
        SmithyHttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        // TODO: this won't as expected right now, because RandomEndpointPlugin doesn't do putIfAbsent.
//        assertThat(request.uri().toString()).matches("http://httpbin.org/anything/random-1\\d/persons/.*");
    }

    @Test
    public void vanillaClient_EndpointResolverPluginExplicitlyAdded() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .addInterceptor(requestCapturingInterceptor)
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .addPlugin(new RandomEndpointPlugin())
            .build();
        callOperation(client);
        SmithyHttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().toString()).matches("http://httpbin.org/anything/random-\\d/persons/.*");
        // assert endpoint used starts with "http://httpbin.org/anything/random-" followed by upto 2 digits
    }

    @Test
    public void vanillaClient_EndpointResolverPluginExplicitlyAdded_EndpointResolverConfigKeyOverridde() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .addInterceptor(requestCapturingInterceptor)
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .addPlugin(new RandomEndpointPlugin())
            .put(RandomEndpointPlugin.BASE, 100)
            .build();
        callOperation(client);
        SmithyHttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        // TODO: this won't as expected right now, because RandomEndpointPlugin doesn't do putIfAbsent.
//        assertThat(request.uri().toString()).matches("http://httpbin.org/anything/random-1\\d/persons/.*");
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
    }

    static final class RandomEndpointPlugin implements ClientPlugin {

        public static final Context.Key<Integer> BASE = Context.key("Random number base");

        @Override
        public void configureClient(ClientConfig.Builder config) {
            config.endpointResolver(new RandomEndpointResolver());
            // TODO: This should be putIfAbsent
            config.put(BASE, 0);
        }

        static final class RandomEndpointResolver implements EndpointResolver {
            private static final Random RANDOM = new Random();
            private static final EndpointProperty<Integer> BASE = EndpointProperty.of("Random number base");

            @Override
            public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
                // TODO: We need something that takes Context.Key BASE and makes it available as EndpointProperty BASE
                //  OR Make the Context available to EndpointResolver.resolveEndpoint()

//                // this makes it required value, which is ok, since plugin sets a default
//                int base = params.properties().get(BASE);
                // TODO: for now it is not flowing so defaulting to 0 here.
                int base = params.properties().get(BASE) != null ? params.properties().get(BASE) : 0;

                int num = base + RANDOM.nextInt(9);
                return CompletableFuture.completedFuture(
                    Endpoint.builder()
                        .uri("http://httpbin.org/anything/random-" + num)
                        .build()
                );
            }
        }
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

    private static void callOperation(PersonDirectoryClient client) {
        PutPersonInput input = PutPersonInput.builder()
            .name("Michael")
            .age(999)
            .favoriteColor("Green")
            .birthday(Instant.now())
            .build();

        PutPersonOutput output = client.putPerson(input);
    }

    private static class RequestCapturingInterceptor implements ClientInterceptor {
        private SmithyHttpRequest request;

        public SmithyHttpRequest lastCapturedRequest() {
            return request;
        }

        @Override
        public void readBeforeTransmit(RequestHook<?, ?> hook) {
            request = (SmithyHttpRequest) hook.request();
        }
    }
}
