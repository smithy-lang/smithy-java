/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.client;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.example.model.GetPersonImage;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPerson;
import software.amazon.smithy.java.runtime.example.model.PutPersonImage;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
final class PersonDirectoryAsyncClientImpl extends Client implements PersonDirectoryAsyncClient {

    PersonDirectoryAsyncClientImpl(PersonDirectoryAsyncClient.Builder builder) {
        super(builder);
    }

    @Override
    public CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input, RequestOverrideConfig overrideConfig) {
        return call(input, new GetPersonImage(), overrideConfig);
    }

    @Override
    public CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input, RequestOverrideConfig overrideConfig) {
        return call(input, new PutPerson(), overrideConfig);
    }

    @Override
    public CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input, RequestOverrideConfig overrideConfig) {
        return call(input, new PutPersonImage(), overrideConfig);
    }

}

