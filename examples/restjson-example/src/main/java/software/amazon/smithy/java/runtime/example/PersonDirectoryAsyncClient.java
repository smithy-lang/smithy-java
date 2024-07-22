/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;

public interface PersonDirectoryAsyncClient {

    default CompletableFuture<GetPersonImageOutput> getPersonImage(
        GetPersonImageInput input,
        ClientPlugin... overridePlugins
    ) {
        return getPersonImage(input, null, overridePlugins);
    }

    CompletableFuture<GetPersonImageOutput> getPersonImage(
        GetPersonImageInput input,
        ClientConfig overrideConfig,
        ClientPlugin... overridePlugins
    );

    default CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input, ClientPlugin... overridePlugins) {
        return putPerson(input, null, overridePlugins);
    }

    CompletableFuture<PutPersonOutput> putPerson(
        PutPersonInput input,
        ClientConfig overrideConfig,
        ClientPlugin... overridePlugins
    );

    default CompletableFuture<PutPersonImageOutput> putPersonImage(
        PutPersonImageInput input,
        ClientPlugin... overridePlugins
    ) {
        return putPersonImage(input, null, overridePlugins);
    }

    CompletableFuture<PutPersonImageOutput> putPersonImage(
        PutPersonImageInput input,
        ClientConfig overrideConfig,
        ClientPlugin... overridePlugins
    );

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends Client.Builder<PersonDirectoryAsyncClient, Builder> {

        private Builder() {}

        @Override
        public PersonDirectoryAsyncClient build() {
            return new PersonDirectoryAsyncClientImpl(this);
        }
    }
}
