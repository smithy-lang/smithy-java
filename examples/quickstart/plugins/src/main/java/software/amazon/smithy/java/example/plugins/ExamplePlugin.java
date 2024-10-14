package software.amazon.smithy.java.example.plugins;

import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;

/**
 * Example plugin that sets the static endpoint for the generated client.
 */
public class ExamplePlugin implements ClientPlugin {
    private static final EndpointResolver STATIC_ENDPOINT = EndpointResolver.staticEndpoint("http://localhost:8888");

    @Override
    public void configureClient(ClientConfig.Builder builder) {
        builder.endpointResolver(STATIC_ENDPOINT);
    }
}
