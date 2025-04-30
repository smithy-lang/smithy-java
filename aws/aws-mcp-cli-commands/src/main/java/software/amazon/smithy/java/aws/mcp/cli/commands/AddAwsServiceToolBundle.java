/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.mcp.cli.commands;

import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.smithy.java.aws.servicebundle.bundler.AwsServiceBundler;
import software.amazon.smithy.java.mcp.cli.AbstractAddToolBundle;
import software.amazon.smithy.java.mcp.cli.model.Bundle;
import software.amazon.smithy.java.mcp.cli.model.GenericArguments;
import software.amazon.smithy.java.mcp.cli.model.Model;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledToolBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.ToolBundleConfig.SmithyModeledMember;

@Command(name = "add-aws-service-tool-bundle")
public class AddAwsServiceToolBundle extends AbstractAddToolBundle<SmithyModeledMember> {

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

    @Override
    protected SmithyModeledMember getNewToolConfig() {
        var bundle = new AwsServiceBundler(awsServiceName).bundle();
        return new SmithyModeledMember(SmithyModeledToolBundleConfig.builder()
                .name(awsServiceName)
                .serviceDescriptor(convert(bundle))
                .build());
    }

    private static Bundle convert(software.amazon.smithy.modelbundle.api.model.Bundle bundle) {
        return Bundle.builder()
                .config(bundle.getConfig())
                .configType(bundle.getConfigType())
                .serviceName(bundle.getServiceName())
                .model(convert(bundle.getModel()))
                .requestArguments(convert(bundle.getRequestArguments()))
                .build();
    }

    private static GenericArguments convert(
            software.amazon.smithy.modelbundle.api.model.GenericArguments genericArguments
    ) {
        return GenericArguments.builder()
                .model(convert(genericArguments.getModel()))
                .identifier(genericArguments.getIdentifier())
                .build();
    }

    private static Model convert(software.amazon.smithy.modelbundle.api.model.Model model) {
        return Model.builder().smithyModel(model.getValue()).build();
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
        return allowedApis;
    }

    @Override
    protected Set<String> blockedTools() {
        return blockedApis;
    }
}
