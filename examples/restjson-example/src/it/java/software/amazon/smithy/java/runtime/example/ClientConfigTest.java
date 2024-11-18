/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.example.client.PersonDirectoryClient;
import software.amazon.smithy.java.runtime.example.model.GetPersonImage;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPerson;
import software.amazon.smithy.java.runtime.example.model.PutPersonImage;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;

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
            .endpointResolver(EndpointResolver.staticHost("http://httpbin.org/anything"))
            .build();
        callOperation(client);
        HttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().toString()).startsWith("http://httpbin.org/anything/");
    }

    @Test
    public void clientWithDefaults() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .build();
        callOperation(client);
        HttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().getHost()).isEqualTo("global.example.com");
    }

    @Test
    public void clientWithDefaults_EndpointResolverOverridden() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .endpointResolver(EndpointResolver.staticHost("http://httpbin.org/anything"))
            .build();
        callOperation(client);
        HttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().toString()).startsWith("http://httpbin.org/anything/");
    }

    @Test
    public void clientWithDefaults_RegionKeyOverridden() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .putConfig(RegionAwareServicePlugin.REGION, "us-west-2")
            .build();
        callOperation(client);
        HttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().getHost()).isEqualTo("us-west-2.example.com");
    }

    @Test
    public void clientWithDefaults_RegionKeyOverridden_DefaultPluginExplicitlyReAdded() {
        PersonDirectoryClient client = PersonDirectoryClientWithDefaults.builder()
            .addInterceptor(requestCapturingInterceptor)
            .addPlugin(new RegionAwareServicePlugin())
            .putConfig(RegionAwareServicePlugin.REGION, "us-west-2")
            .build();
        callOperation(client);
        HttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().getHost()).isEqualTo("us-west-2.example.com");
    }

    @Test
    public void vanillaClient_RegionPluginExplicitlyAdded() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .addInterceptor(requestCapturingInterceptor)
            .addPlugin(new RegionAwareServicePlugin())
            .build();
        callOperation(client);
        HttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().getHost()).isEqualTo("global.example.com");
    }

    @Test
    public void vanillaClient_RegionPluginExplicitlyAdded_RegionKeyOverridden() {
        PersonDirectoryClient client = PersonDirectoryClient.builder()
            .addInterceptor(requestCapturingInterceptor)
            .addPlugin(new RegionAwareServicePlugin())
            .putConfig(RegionAwareServicePlugin.REGION, "us-west-2")
            .build();
        callOperation(client);
        HttpRequest request = requestCapturingInterceptor.lastCapturedRequest();
        assertThat(request.uri().getHost()).isEqualTo("us-west-2.example.com");
    }

    private static final class PersonDirectoryClientWithDefaults extends Client implements PersonDirectoryClient {
        public PersonDirectoryClientWithDefaults(PersonDirectoryClientWithDefaults.Builder builder) {
            super(builder);
        }

        @Override
        public GetPersonImageOutput getPersonImage(GetPersonImageInput input, RequestOverrideConfig overrideConfig) {
            return call(input, new GetPersonImage(), overrideConfig).join();
        }

        @Override
        public PutPersonOutput putPerson(PutPersonInput input, RequestOverrideConfig overrideConfig) {
            return call(input, new PutPerson(), overrideConfig).join();
        }

        @Override
        public PutPersonImageOutput putPersonImage(PutPersonImageInput input, RequestOverrideConfig overrideConfig) {
            return call(input, new PutPersonImage(), overrideConfig).join();
        }

        static PersonDirectoryClientWithDefaults.Builder builder() {
            return new PersonDirectoryClientWithDefaults.Builder();
        }

        static final class Builder extends
            Client.Builder<PersonDirectoryClient, PersonDirectoryClientWithDefaults.Builder> {

            private Builder() {
                ClientConfig.Builder configBuilder = configBuilder();
                configBuilder.protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()));
                configBuilder.transport(new JavaHttpClientTransport(HttpClient.newHttpClient()));

                List<ClientPlugin> defaultPlugins = List.of(new RegionAwareServicePlugin());
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

    static final class RegionAwareServicePlugin implements ClientPlugin {

        public static final Context.Key<String> REGION = Context.key("Region for the service");

        @Override
        public void configureClient(ClientConfig.Builder config) {
            config.endpointResolver(new RegionalEndpointResolver());
            config.putConfigIfAbsent(REGION, "global");
        }

        static final class RegionalEndpointResolver implements EndpointResolver {

            @Override
            public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
                String region = params.context().get(REGION);
                return CompletableFuture.completedFuture(
                    Endpoint.builder()
                        .uri("http://" + region + ".example.com")
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

        try {
            client.putPerson(input);
        } catch (Exception ignored) {
            // This exception is ignored.
        }
    }

    private static class RequestCapturingInterceptor implements ClientInterceptor {
        private HttpRequest request;

        public HttpRequest lastCapturedRequest() {
            return request;
        }

        @Override
        public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
            request = (HttpRequest) hook.request();
        }
    }
}
