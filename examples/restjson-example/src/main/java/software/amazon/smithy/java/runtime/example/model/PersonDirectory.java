/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.core.Context;

// An example of a generated service interface.
public interface PersonDirectory {

    // Each operation generates two methods: one that takes context and one that doesn't.
    default PutPersonOutput putPerson(PutPersonInput input) {
        return putPerson(input, Context.create());
    }

    default PutPersonOutput putPerson(PutPersonInput input, Context context) {
        return putPersonAsync(input, context).join();
    }

    default CompletableFuture<PutPersonOutput> putPersonAsync(PutPersonInput input) {
        return putPersonAsync(input, Context.create());
    }

    CompletableFuture<PutPersonOutput> putPersonAsync(PutPersonInput input, Context context);

    default PutPersonImageOutput putPersonImage(PutPersonImageInput input) {
        return putPersonImage(input, Context.create());
    }

    default PutPersonImageOutput putPersonImage(PutPersonImageInput input, Context context) {
        return putPersonImageAsync(input, context).join();
    }

    default CompletableFuture<PutPersonImageOutput> putPersonImageAsync(PutPersonImageInput input) {
        return putPersonImageAsync(input, Context.create());
    }

    CompletableFuture<PutPersonImageOutput> putPersonImageAsync(PutPersonImageInput input, Context context);

    default GetPersonImageOutput getPersonImage(GetPersonImageInput input) {
        return getPersonImage(input, Context.create());
    }

    default GetPersonImageOutput getPersonImage(GetPersonImageInput input, Context context) {
        return getPersonImageAsync(input, context).join();
    }

    default CompletableFuture<GetPersonImageOutput> getPersonImageAsync(GetPersonImageInput input) {
        return getPersonImageAsync(input, Context.create());
    }

    CompletableFuture<GetPersonImageOutput> getPersonImageAsync(GetPersonImageInput input, Context context);
}
