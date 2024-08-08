

package smithy.java.codegen.server.test.client;

import java.util.concurrent.CompletableFuture;
import smithy.java.codegen.server.test.model.Echo;
import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
final class TestServiceAsyncClientImpl extends Client implements TestServiceAsyncClient {

    TestServiceAsyncClientImpl(TestServiceAsyncClient.Builder builder) {
        super(builder);
    }

    @Override
    public CompletableFuture<EchoOutput> echo(EchoInput input, RequestOverrideConfig overrideConfig) {
        return call(input, new Echo(), overrideConfig);
    }

}

