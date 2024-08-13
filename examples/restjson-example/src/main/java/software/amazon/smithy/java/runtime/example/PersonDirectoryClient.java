/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

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

public interface PersonDirectoryClient {

    default GetPersonImageOutput getPersonImage(GetPersonImageInput input) {
        return getPersonImage(input, null);
    }

    GetPersonImageOutput getPersonImage(GetPersonImageInput input, RequestOverrideConfig overrideConfig);

    default PutPersonOutput putPerson(PutPersonInput input) {
        return putPerson(input, null);
    }

    PutPersonOutput putPerson(PutPersonInput input, RequestOverrideConfig overrideConfig);

    default PutPersonImageOutput putPersonImage(PutPersonImageInput input) {
        return putPersonImage(input, null);
    }

    PutPersonImageOutput putPersonImage(PutPersonImageInput input, RequestOverrideConfig overrideConfig);

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends Client.Builder<PersonDirectoryClient, Builder> {
        private static final ProtocolSettings settings = ProtocolSettings.builder().namespace("foo.bar").build();
        private static final RestJson1Trait protocolTrait = RestJson1Trait.builder().build();
        private static final ClientProtocolFactory<RestJson1Trait> factory = new RestJsonClientProtocol.Factory();

        private Builder() {
            configBuilder().protocol(factory.createProtocol(settings, protocolTrait));
        }

        @Override
        public PersonDirectoryClient build() {
            return new PersonDirectoryClientImpl(this);
        }
    }
}
