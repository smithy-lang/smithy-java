/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.mcp.cli.commands;

import static picocli.CommandLine.ArgGroup;
import static picocli.CommandLine.Parameters;

import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.smithy.java.aws.servicebundle.bundler.AwsServiceBundler;
import software.amazon.smithy.java.mcp.cli.AbstractCreateBundle;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;
import software.amazon.smithy.mcp.bundle.api.model.SmithyMcpBundle;

@Command(name = "aws-service-mcp", description = "Create an MCP for an AWS service.")
public class CreateAwsServiceBundle extends AbstractCreateBundle<CreateAwsServiceBundle.CreateAwsServiceBundleInput> {

    @ArgGroup(multiplicity = "1")
    Input input;

    @Override
    protected CreateAwsServiceBundleInput getInput() {
        return input.cliInput;
    }

    @Override
    protected Bundle getNewBundle(CreateAwsServiceBundleInput input) {
        var bundleBuilder = AwsServiceBundler.builder()
                .serviceName(input.awsServiceName);
        if (input.allowedApis != null) {
            // User explicitly specified allowed APIs
            bundleBuilder.exposedOperations(input.allowedApis);
            // If readOnlyApis is also requested, include those as well
            if (Boolean.TRUE.equals(input.readOnlyApis)) {
                bundleBuilder.readOnlyOperations();
            }
        } else if (!Boolean.FALSE.equals(input.readOnlyApis)) {
            //If nothing is specified then default to only readOnlyOperations.
            bundleBuilder.readOnlyOperations();
        } else {
            throw new IllegalArgumentException("You have turned off readOnlyApis and also not specified any " +
                    "allowedApis so there are no operations to create a bundle for.");
        }

        if (input.blockedApis != null) {
            // Always apply blocked operations if specified
            bundleBuilder.blockedOperations(input.blockedApis);
        }
        return Bundle.builder()
                .smithyBundle(SmithyMcpBundle.builder()
                        .bundle(bundleBuilder.build().bundle())
                        .metadata(BundleMetadata.builder()
                                .name(input.name)
                                .description(input.description)
                                .build())
                        .build())
                .build();
    }

    public static class Input {
        @ArgGroup(exclusive = false)
        CreateAwsServiceBundleInput cliInput;

        @ArgGroup(multiplicity = "1")
        JsonInput jsonInput;

    }

    public static class CreateAwsServiceBundleInput extends CreateBundleInput {
        @Parameters(description = "Name of aws service to create the bundle for.")
        String awsServiceName;

        @Option(names = {"-a", "--allowed-apis"}, description = "List of APIs to expose in the MCP server")
        protected Set<String> allowedApis;

        @Option(names = {"-b", "--blocked-apis"}, description = "List of APIs to hide in the MCP server")
        protected Set<String> blockedApis;

        @Option(names = "--read-only-apis",
                description = "Include read only APIs in the MCP server")
        protected Boolean readOnlyApis;
    }
}
