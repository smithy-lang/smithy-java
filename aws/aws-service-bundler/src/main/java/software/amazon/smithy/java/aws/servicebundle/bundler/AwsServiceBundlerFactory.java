/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.bundler;

import software.amazon.smithy.modelbundle.api.Bundler;
import software.amazon.smithy.modelbundle.api.BundlerFactory;

public final class AwsServiceBundlerFactory implements BundlerFactory {
    @Override
    public String identifier() {
        return "aws";
    }

    @Override
    public Bundler newBundler(String... args) {
        return new AwsServiceBundler(args);
    }
}
