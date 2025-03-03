/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

/**
 * A plugin modifies a CLI's configuration when the CLI is created.
 */
@FunctionalInterface
public interface CliPlugin {
    /**
     * Modify the provided client configuration.
     */
    void configureCli(CliConfig.Builder builder);
}
