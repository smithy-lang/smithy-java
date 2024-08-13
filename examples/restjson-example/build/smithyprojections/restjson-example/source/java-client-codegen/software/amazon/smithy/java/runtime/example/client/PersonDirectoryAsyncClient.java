/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.client;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.runtime.client.core.ProtocolSettings;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public interface PersonDirectoryAsyncClient {

    default CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input) {
        return getPersonImage(input, null);
    }

    CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input, RequestOverrideConfig overrideConfig);

    default CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input) {
        return putPerson(input, null);
    }

    CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input, RequestOverrideConfig overrideConfig);

    default CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input) {
        return putPersonImage(input, null);
    }

    CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input, RequestOverrideConfig overrideConfig);

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends Client.Builder<PersonDirectoryAsyncClient, Builder> {
        private static final ProtocolSettings settings = ProtocolSettings.builder()
                .namespace("software.amazon.smithy.java.runtime.example")
                .build();
        private static final RestJson1Trait protocolTrait = new RestJson1Trait.Provider().createTrait(
            ShapeId.from("aws.protocols#restJson1"),
            Node.objectNodeBuilder()
                .build()
        );
        private static final ClientProtocolFactory<RestJson1Trait> factory = new RestJsonClientProtocol.Factory();

        private Builder() {
            configBuilder().protocol(factory.createProtocol(settings, protocolTrait));
        }

        @Override
        public PersonDirectoryAsyncClient build() {
            return new PersonDirectoryAsyncClientImpl(this);
        }
    }
}

