/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.provider;

import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.modelbundle.api.BundlePlugin;
import software.amazon.smithy.modelbundle.api.BundlePluginFactory;

public final class AwsServiceBundlePluginFactory implements BundlePluginFactory {
    public AwsServiceBundlePluginFactory() {

    }

    @Override
    public String identifier() {
        return "aws";
    }

    @Override
    public BundlePlugin createBundlePlugin(Document input) {
        return new AwsServiceBundle(input.asShape(AwsServiceMetadata.builder()));
    }
}
