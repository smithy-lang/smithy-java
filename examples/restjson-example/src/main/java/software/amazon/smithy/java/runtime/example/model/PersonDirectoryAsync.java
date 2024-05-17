/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.core.Context;

// An example of a generated async service interface.
public interface PersonDirectoryAsync {

    default CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input) {
        return putPerson(input, Context.create());
    }

    CompletableFuture<PutPersonOutput> putPerson(PutPersonInput input, Context context);

    default CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input) {
        return putPersonImage(input, Context.create());
    }

    CompletableFuture<PutPersonImageOutput> putPersonImage(PutPersonImageInput input, Context context);

    default CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input) {
        return getPersonImage(input, Context.create());
    }

    CompletableFuture<GetPersonImageOutput> getPersonImage(GetPersonImageInput input, Context context);
}
