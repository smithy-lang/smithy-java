/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.provider;

import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.modelbundle.api.BundleClientPluginProvider;
import software.amazon.smithy.modelbundle.api.BundleClientPluginProviderFactory;

public final class AwsAuthProviderFactory implements BundleClientPluginProviderFactory {
    public AwsAuthProviderFactory() {

    }

    @Override
    public String identifier() {
        return "aws";
    }

    @Override
    public BundleClientPluginProvider createAuthFactory(Document input) {
        return new AwsAuthProvider(input.asShape(AwsServiceMetadata.builder()));
    }
}
