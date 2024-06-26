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
import software.amazon.smithy.java.runtime.example.model.PutPerson;
import software.amazon.smithy.java.runtime.example.model.PutPersonImage;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;

final class PersonDirectoryAsyncClientImpl extends Client implements PersonDirectoryAsyncClient {

    PersonDirectoryAsyncClientImpl(PersonDirectoryAsyncClient.Builder builder) {
        super(builder);
    }

    @Override
    public CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input, Context context) {
        return call(input, new PutPerson(), context);
    }

    @Override
    public CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input, Context context) {
        return call(input, new PutPersonImage(), context);
    }

    @Override
    public CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input, Context context) {
        return call(input, new GetPersonImage(), context);
    }
}
