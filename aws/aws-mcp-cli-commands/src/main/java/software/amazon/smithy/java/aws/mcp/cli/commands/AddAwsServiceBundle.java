/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.mcp.cli.commands;

import java.util.Collections;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.smithy.java.aws.servicebundle.bundler.AwsServiceBundler;
import software.amazon.smithy.java.mcp.cli.AbstractAddBundle;
import software.amazon.smithy.java.mcp.cli.CliBundle;
import software.amazon.smithy.java.mcp.cli.model.McpBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledBundleConfig;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;
import software.amazon.smithy.mcp.bundle.api.model.SmithyMcpBundle;

@Command(name = "add-aws-bundle")
public class AddAwsServiceBundle extends AbstractAddBundle {

    @Option(names = "--overwrite",
            description = "Overwrite existing config",
            defaultValue = "false")
    protected boolean overwrite;

    @Option(names = {"-n", "--name"}, description = "Name of the AWS Service.", required = true)
    protected String awsServiceName;

    @Option(names = {"-a", "--allowed-apis"}, description = "List of APIs to expose in the MCP server")
    protected Set<String> allowedApis;

    @Option(names = {"-b", "--blocked-apis"}, description = "List of APIs to hide in the MCP server")
    protected Set<String> blockedApis;

    @Option(names = "--include-write-apis",
            description = "Include write APIs in the MCP server",
            defaultValue = "false")
    boolean includeWriteApis;

    @Override
    protected CliBundle getNewToolConfig() {
        var bundleBuilder = AwsServiceBundler.builder()
                .serviceName(awsServiceName)
                .exposedOperations(allowedTools())
                .blockedOperations(blockedTools());
        if (!includeWriteApis) {
            bundleBuilder = bundleBuilder.readOnlyOperations();
        }
        var bundle = bundleBuilder.build().bundle();

        var bundleConfig = McpBundleConfig.builder()
                .smithyModeled(SmithyModeledBundleConfig.builder()
                        .name(awsServiceName)
                        .bundleLocation(getBundleFileLocation())
                        .build())
                .build();
        return new CliBundle(
                Bundle.builder()
                        .smithyBundle(SmithyMcpBundle.builder()
                                .bundle(bundle)
                                .metadata(BundleMetadata.builder().name(awsServiceName).build())
                                .build())
                        .build(),
                bundleConfig);
    }

    @Override
    protected String getToolBundleName() {
        return awsServiceName;
    }

    @Override
    protected boolean canOverwrite() {
        return overwrite;
    }

    @Override
    protected Set<String> allowedTools() {
        return allowedApis == null ? Collections.emptySet() : allowedApis;
    }

    @Override
    protected Set<String> blockedTools() {
        return blockedApis == null ? Collections.emptySet() : blockedApis;
    }
}
