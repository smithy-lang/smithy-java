/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.mcp.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import software.amazon.smithy.java.aws.servicebundle.bundler.AwsServiceBundler;
import software.amazon.smithy.mcp.bundle.api.Registry;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;
import software.amazon.smithy.mcp.bundle.api.model.SmithyMcpBundle;

public final class AwsMcpRegistry implements Registry {

    private final List<RegistryEntry> availableMcpBundles;

    public AwsMcpRegistry() {
        try (var models = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(AwsMcpRegistry.class.getResourceAsStream("/models.txt")),
                StandardCharsets.UTF_8))) {
            this.availableMcpBundles = models.lines()
                    .map(AwsMcpRegistry::lineToEntry)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static RegistryEntry lineToEntry(String line) {
        // line format: Title (which may contain spaces)|sdk-id/service/YYYY/MM/DD/sdk-id-YYYY-MM-DD.json
        // sdk title, pipe character, github url to download the model (of which the first component is the sdk id)
        var parts = line.split("\\|");
        // this is the SDK title
        var title = parts[0];
        // this is id we pass to start-server
        var name = parts[1].split("/")[0].toLowerCase(Locale.ROOT);
        var bundle = BundleMetadata.builder().name(name).description("AWS MCP server for " + name).build();
        return new RegistryEntry(title, bundle);
    }

    @Override
    public String name() {
        return "aws-mcp-registry";
    }

    @Override
    public Stream<RegistryEntry> listMcpBundles() {
        return availableMcpBundles.stream();
    }

    @Override
    public Bundle getMcpBundle(String id) {
        var bundle = AwsServiceBundler.builder().serviceName(id).build().bundle();
        return Bundle.builder()
                .smithyBundle(SmithyMcpBundle.builder().bundle(bundle).build())
                .build();
    }
}
