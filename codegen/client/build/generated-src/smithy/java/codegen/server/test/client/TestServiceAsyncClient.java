

package smithy.java.codegen.server.test.client;

import java.util.concurrent.CompletableFuture;
import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import software.amazon.smithy.java.codegen.client.TestAuthScheme;
import software.amazon.smithy.java.runtime.client.core.Client;
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

        private Builder() {
            configBuilder().putSupportedAuthSchemes(new TestAuthScheme());
        }

        @Override
        public TestServiceAsyncClient build() {
            return new TestServiceAsyncClientImpl(this);
        }
    }
}

