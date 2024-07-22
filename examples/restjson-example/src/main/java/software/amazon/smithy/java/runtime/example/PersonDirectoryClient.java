/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;

public interface PersonDirectoryClient {

    default GetPersonImageOutput getPersonImage(GetPersonImageInput input, ClientPlugin... plugins) {
        return getPersonImage(input, null, plugins);
    }

    GetPersonImageOutput getPersonImage(
        GetPersonImageInput input,
        ClientConfig overrideConfig,
        ClientPlugin... overridePlugins
    );

    default PutPersonOutput putPerson(PutPersonInput input, ClientPlugin... plugins) {
        return putPerson(input, null, plugins);
    }

    PutPersonOutput putPerson(PutPersonInput input, ClientConfig overrideConfig, ClientPlugin... overridePlugins);

    default PutPersonImageOutput putPersonImage(PutPersonImageInput input, ClientPlugin... overridePlugins) {
        return putPersonImage(input, null, overridePlugins);
    }

    PutPersonImageOutput putPersonImage(
        PutPersonImageInput input,
        ClientConfig overrideConfig,
        ClientPlugin... overridePlugins
    );

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends Client.Builder<PersonDirectoryClient, Builder> {

        private Builder() {}

        @Override
        public PersonDirectoryClient build() {
            return new PersonDirectoryClientImpl(this);
        }
    }
}
