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
import software.amazon.smithy.java.aws.servicebundle.bundler.AwsServiceBundler;
import software.amazon.smithy.java.mcp.cli.Registry;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;
import software.amazon.smithy.mcp.bundle.api.model.SmithyMcpBundle;

public class AwsMcpRegistry implements Registry {

    private final List<BundleMetadata> availableMcpBundles;

    public AwsMcpRegistry() {
        try (var models = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(AwsMcpRegistry.class.getResourceAsStream("/models.txt")),
                StandardCharsets.UTF_8))) {
            this.availableMcpBundles = models.lines()
                    .map(line -> line.substring(0, line.indexOf("/")).toLowerCase(Locale.ROOT))
                    .map(s -> BundleMetadata.builder().name(s).build())
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String name() {
        return "aws-mcp-registry";
    }

    @Override
    public List<BundleMetadata> listMcpBundles() {
        return availableMcpBundles;
    }

    @Override
    public Bundle getMcpBundle(String name) {
        var bundle = new AwsServiceBundler(name).bundle();
        return Bundle.builder()
                .smithyBundle(SmithyMcpBundle.builder().bundle(bundle).build())
                .build();
    }
}
