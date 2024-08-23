

package smithy.java.codegen.server.test.client;

import java.util.List;
import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import software.amazon.smithy.java.codegen.client.TestAuthScheme;
import software.amazon.smithy.java.codegen.client.TestClientPlugin;
import software.amazon.smithy.java.runtime.auth.api.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.http.JavaHttpClientTransport;
import software.amazon.smithy.model.traits.HttpBasicAuthTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public interface TestServiceClient {

    default EchoOutput echo(EchoInput input) {
        return echo(input, null);
    }

    EchoOutput echo(EchoInput input, RequestOverrideConfig overrideConfig);

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends Client.Builder<TestServiceClient, Builder> {
        private final TestClientPlugin testClientPlugin = new TestClientPlugin();
        private final List<ClientPlugin> defaultPlugins = List.of(testClientPlugin);

        private static final HttpBasicAuthTrait httpBasicAuthScheme = (HttpBasicAuthTrait) new HttpBasicAuthTrait();
        private static final AuthSchemeFactory<HttpBasicAuthTrait> httpBasicAuthSchemeFactory = new TestAuthScheme.Factory();

        private Builder() {

            configBuilder().putSupportedAuthSchemes(httpBasicAuthSchemeFactory.createAuthScheme(httpBasicAuthScheme));
            configBuilder().transport(new JavaHttpClientTransport());
        }

        public Builder value(long value) {
            testClientPlugin.value(value);
            return this;
        }

        public Builder value(double value) {
            testClientPlugin.value(value);
            return this;
        }

        public Builder multiValue(String multiValue, String multiValue1) {
            testClientPlugin.multiValue(multiValue, multiValue1);
            return this;
        }

        public Builder singleVarargs(String... singleVarargs) {
            testClientPlugin.singleVarargs(singleVarargs);
            return this;
        }

        public Builder multiVarargs(String multiVarargs, String... multiVarargs1) {
            testClientPlugin.multiVarargs(multiVarargs, multiVarargs1);
            return this;
        }

        @Override
        public TestServiceClient build() {
            for (var plugin : defaultPlugins) {
                plugin.configureClient(configBuilder());
            }
            return new TestServiceClientImpl(this);
        }
    }
}

