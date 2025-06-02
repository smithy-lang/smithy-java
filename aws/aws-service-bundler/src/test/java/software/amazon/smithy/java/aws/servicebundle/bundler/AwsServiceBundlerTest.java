/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.bundler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.model.loader.ModelAssembler;

public class AwsServiceBundlerTest {

    @Test
    public void accessAnalyzer() {
        var bundler = AwsServiceBundler.builder()
                .serviceName("access-analyzer")
                .resolver(serviceName -> getModel("accessanalyzer-2019-11-01.json"))
                .build();
        var bundle = bundler.bundle();
        var config = bundle.getConfig().asShape(AwsServiceMetadata.builder());

        assertEquals("access-analyzer", config.getSigv4SigningName());
        assertEquals("AccessAnalyzer", config.getServiceName());

        assertNotEquals(0, config.getEndpoints().size());
    }

    @Test
    public void testFilteringApis() {
        var filteredOperations = Set.of("GetFindingsStatistics", "GetFindingRecommendation");
        var bundler = AwsServiceBundler.builder()
                .serviceName("access-analyzer")
                .resolver(serviceName -> getModel("accessanalyzer-2019-11-01.json"))
                .exposedOperations(filteredOperations)
                .build();;
        var bundle = bundler.bundle();
        var bundleModel = new ModelAssembler().addUnparsedModel("model.json", bundle.getModel())
                .disableValidation()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        assertThat(bundleModel.getOperationShapes())
                .hasSize(2)
                .filteredOn(o -> !filteredOperations.contains(o.getId().getName()))
                .isEmpty();
    }

    @Test
    public void cw() {
        assertThat(AwsServiceBundler.builder().serviceName("cloudwatch-logs").build().bundle())
                .isNotNull();
    }

    @Test
    public void testReadAndWritePrefixes() {
        var bundler = AwsServiceBundler.builder()
                .serviceName("dynamodb")
                .resolver(serviceName -> getModel("dynamodb-2012-08-10.json"))
                .allowedPrefixes(ApiStandardTerminology.READ_ONLY_API_PREFIXES)
                .blockedPrefixes(ApiStandardTerminology.WRITE_API_PREFIXES)
                .build();
        var bundle = bundler.bundle();
        var bundleModel = new ModelAssembler().addUnparsedModel("model.json", bundle.getModel())
                .disableValidation()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();

        var readOnlyOperations = Set.of("GetResourcePolicy", "DescribeImport", "ListTables", "GetItem", "BatchGetItem");
        var writeOperations = Set.of("UpdateItem", "CreateTable", "WriteItem", "BatchWriteItem", "DeleteTable");

        assertThat(bundleModel.getOperationShapes())
                .filteredOn(o -> readOnlyOperations.contains(o.getId().getName()))
                .hasSize(readOnlyOperations.size());

        assertThat(bundleModel.getOperationShapes())
                .filteredOn(o -> writeOperations.contains(o.getId().getName()))
                .isEmpty();
    }

    private static String getModel(String path) {
        try (var stream = new InputStreamReader(Objects
                .requireNonNull(AwsServiceBundlerTest.class.getResourceAsStream(path), "No model named " + path))) {
            var builder = new StringBuilder();
            var buffer = new char[1024];
            while (true) {
                var read = stream.read(buffer);
                if (read == -1) {
                    break;
                }
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
