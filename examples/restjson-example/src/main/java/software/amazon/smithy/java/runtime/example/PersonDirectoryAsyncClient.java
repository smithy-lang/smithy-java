/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;

public interface PersonDirectoryAsyncClient {

    default CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input) {
        return getPersonImage(input, Context.create());
    }

    CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input, Context context);

    default CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input) {
        return putPerson(input, Context.create());
    }

    CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input, Context context);

    default CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input) {
        return putPersonImage(input, Context.create());
    }

    CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input, Context context);

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
