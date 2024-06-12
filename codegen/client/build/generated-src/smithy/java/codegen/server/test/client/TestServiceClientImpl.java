

package smithy.java.codegen.server.test.client;

import smithy.java.codegen.server.test.model.Echo;
import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
final class TestServiceClientImpl extends Client implements TestServiceClient {

    TestServiceClientImpl(TestServiceClient.Builder builder) {
        super(builder);
    }

    @Override
    public EchoOutput echo(EchoInput input, Context context) {
        return call(input, null, null, new Echo(), context).join();
    }

}

