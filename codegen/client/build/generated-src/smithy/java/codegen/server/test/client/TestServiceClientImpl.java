

package smithy.java.codegen.server.test.client;

import smithy.java.codegen.server.test.model.Echo;
import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
final class TestServiceClientImpl extends Client implements TestServiceClient {

    TestServiceClientImpl(TestServiceClient.Builder builder) {
        super(builder);
    }

    @Override
    public EchoOutput echo(EchoInput input, RequestOverrideConfig overrideConfig) {
        return call(input, new Echo(), overrideConfig).join();
    }

}

