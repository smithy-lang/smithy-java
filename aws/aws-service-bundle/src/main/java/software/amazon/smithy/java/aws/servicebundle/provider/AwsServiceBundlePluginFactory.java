/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.provider;

import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.modelbundle.api.BundleClientPluginProvider;
import software.amazon.smithy.modelbundle.api.BundleClientPluginProviderFactory;

public final class AwsServiceBundlePluginFactory implements BundleClientPluginProviderFactory {
    public AwsServiceBundlePluginFactory() {

    }

    @Override
    public String identifier() {
        return "aws";
    }

    @Override
    public BundleClientPluginProvider createPluginProvider(Document input) {
        return new AwsServiceBundle(input.asShape(AwsServiceMetadata.builder()));
    }
}
