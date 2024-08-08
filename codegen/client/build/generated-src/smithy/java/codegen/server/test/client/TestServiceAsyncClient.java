

package smithy.java.codegen.server.test.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import software.amazon.smithy.java.codegen.client.TestClientPlugin;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public interface TestServiceAsyncClient {

    default CompletableFuture<EchoOutput> echo(EchoInput input) {
        return echo(input, null);
    }

    CompletableFuture<EchoOutput> echo(EchoInput input, RequestOverrideConfig overrideConfig);

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends Client.Builder<TestServiceAsyncClient, Builder> {
        private final TestClientPlugin testClientPlugin = new TestClientPlugin();
        private final List<ClientPlugin> defaultPlugins = List.of(testClientPlugin);

        private Builder() {}

        public Builder value(long value) {
            testClientPlugin.value(value);
            return this;
        }

        public Builder value(double value) {
            testClientPlugin.value(value);
            return this;
        }

        public Builder multiValue(String arg0, String arg1) {
            testClientPlugin.multiValue(arg0, arg1);
            return this;
        }

        public Builder singleVarargs(String... arg0) {
            testClientPlugin.singleVarargs(arg0);
            return this;
        }

        public Builder multiVarargs(String arg0, String... arg1) {
            testClientPlugin.multiVarargs(arg0, arg1);
            return this;
        }

        @Override
        public TestServiceAsyncClient build() {
            for (var plugin : defaultPlugins) {
                plugin.configureClient(configBuilder());
            }
            return new TestServiceAsyncClientImpl(this);
        }
    }
}

