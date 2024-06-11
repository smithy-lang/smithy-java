/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.example.model.GetPersonImage;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PersonDirectoryAsync;
import software.amazon.smithy.java.runtime.example.model.PutPerson;
import software.amazon.smithy.java.runtime.example.model.PutPersonImage;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;

public final class PersonDirectoryAsyncClient extends Client implements PersonDirectoryAsync {

    private PersonDirectoryAsyncClient(Builder builder) {
        super(builder);
    }

    @Override
    public CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input, Context context) {
        return call(input, null, null, new PutPerson(), context);
    }

    @Override
    public CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input, Context context) {
        return call(input, input.image(), null, new PutPersonImage(), context);
    }

    @Override
    public CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input, Context context) {
        return call(input, null, null, new GetPersonImage(), context);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends Client.Builder<Builder> {

        private Builder() {}

        @Override
        public PersonDirectoryAsyncClient build() {
            return new PersonDirectoryAsyncClient(this);
        }
    }
}
