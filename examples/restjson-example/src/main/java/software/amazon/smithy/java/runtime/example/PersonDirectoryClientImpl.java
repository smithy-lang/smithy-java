/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

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

final class PersonDirectoryClientImpl extends Client implements PersonDirectoryClient {

    PersonDirectoryClientImpl(PersonDirectoryClient.Builder builder) {
        super(builder);
    }

    @Override
    public PutPersonOutput putPerson(
        PutPersonInput input,
        RequestOverrideConfig overrideConfig
    ) {
        return call(input, new PutPerson(), overrideConfig).join();
    }

    @Override
    public PutPersonImageOutput putPersonImage(
        PutPersonImageInput input,
        RequestOverrideConfig overrideConfig
    ) {
        return call(input, new PutPersonImage(), overrideConfig).join();
    }

    @Override
    public GetPersonImageOutput getPersonImage(
        GetPersonImageInput input,
        RequestOverrideConfig overrideConfig
    ) {
        return call(input, new GetPersonImage(), overrideConfig).join();
    }
}
