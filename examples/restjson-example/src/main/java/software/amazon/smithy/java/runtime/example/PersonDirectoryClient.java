/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

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

public final class PersonDirectoryClient extends Client {

    private PersonDirectoryClient(Builder builder) {
        super(builder);
    }

    public GetPersonImageOutput getPersonImage(GetPersonImageInput input) {
        return getPersonImage(input, Context.create());
    }

    public GetPersonImageOutput getPersonImage(GetPersonImageInput input, Context context) {
        return call(input, null, null, new GetPersonImage(), context).join();
    }

    public PutPersonOutput putPerson(PutPersonInput input) {
        return putPerson(input, Context.create());
    }

    public PutPersonOutput putPerson(PutPersonInput input, Context context) {
        return call(input, null, null, new PutPerson(), context).join();
    }

    public PutPersonImageOutput putPersonImage(PutPersonImageInput input) {
        return putPersonImage(input, Context.create());
    }

    public PutPersonImageOutput putPersonImage(PutPersonImageInput input, Context context) {
        return call(input, input.image(), null, new PutPersonImage(), context).join();
    }

    public static PersonDirectoryClient.Builder builder() {
        return new PersonDirectoryClient.Builder();
    }

    public static final class Builder extends Client.Builder<Builder> {

        private Builder() {}

        @Override
        public PersonDirectoryClient build() {
            return new PersonDirectoryClient(this);
        }
    }
}
