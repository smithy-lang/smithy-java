/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.provider;

import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.modelbundle.api.ConfigProvider;
import software.amazon.smithy.modelbundle.api.ConfigProviderFactory;

public final class AwsAuthProviderFactory implements ConfigProviderFactory {
    public AwsAuthProviderFactory() {

    }

    @Override
    public String identifier() {
        return "aws";
    }

    @Override
    public ConfigProvider<?> createAuthFactory(Document input) {
        return new AwsAuthProvider(input.asShape(AwsServiceMetadata.builder()));
    }
}
