

package smithy.java.codegen.server.test.client;

import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public interface TestServiceClient {

    default EchoOutput echo(EchoInput input) {
        return echo(input, Context.create());
    }

    EchoOutput echo(EchoInput input, Context context);

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends Client.Builder<TestServiceClient, Builder> {

        private Builder() {}

        @Override
        public TestServiceClient build() {
            return new TestServiceClientImpl(this);
        }
    }
}

