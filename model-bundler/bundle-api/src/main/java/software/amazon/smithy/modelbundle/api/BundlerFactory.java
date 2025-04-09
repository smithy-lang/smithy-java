/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

public interface BundlerFactory {
    String identifier();

    Bundler newBundler(String... args);
}
