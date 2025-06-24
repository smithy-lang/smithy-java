/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.bundler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ModelSerializer;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;

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
    public void resourceApis() {
        var bundler = AwsServiceBundler.builder()
                .serviceName("access-analyzer")
                .resolver(serviceName -> getModel("accessanalyzer-2019-11-01.json"))
                .readOnlyOperations()
                .build();
        var bundle = bundler.bundle();
        var bundleModel = new ModelAssembler().addUnparsedModel("model.json", bundle.getModel())
                .disableValidation()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        //read resource APIS "GET", "LIST" should be present
        assertNotNull(bundleModel.expectShape(ShapeId.from("com.amazonaws.accessanalyzer#GetAnalyzer"),
                OperationShape.class));
        assertNotNull(bundleModel.expectShape(ShapeId.from("com.amazonaws.accessanalyzer#ListAnalyzers"),
                OperationShape.class));

        //Write APIs should not be present.
        assertThat(bundleModel.getShape(ShapeId.from("com.amazonaws.accessanalyzer#DeleteAnalyzer"))).isEmpty();
    }

    @Test
    public void testFilteringApis() {
        var filteredOperations = Set.of("GetFindingsStatistics", "GetFindingRecommendation");
        var bundler = AwsServiceBundler.builder()
                .serviceName("access-analyzer")
                .resolver(serviceName -> getModel("accessanalyzer-2019-11-01.json"))
                .exposedOperations(filteredOperations)
                .build();
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
        assertThat(AwsServiceBundler.builder().serviceName("cloudwatch").build().bundle())
                .isNotNull();
    }

    @Test
    public void testReadAndWritePrefixes() {
        var bundler = AwsServiceBundler.builder()
                .serviceName("dynamodb")
                .resolver(serviceName -> getModel("dynamodb-2012-08-10.json"))
                .allowedPrefixes(ApiStandardTerminology.getReadOnlyApiPrefixes())
                .blockedPrefixes(ApiStandardTerminology.getWriteApiPrefixes())
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

    @Test
    void testOnlyOneServiceRetained() {
        var combinedModel = new ModelAssembler()
                .addUnparsedModel("model1.json", getModel("dynamodb-2012-08-10.json"))
                .addUnparsedModel("model2.json", getModel("accessanalyzer-2019-11-01.json"))
                .disableValidation()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        var totalShapes = combinedModel.getShapeIds().size();
        var serializedModel = Node.printJson(ModelSerializer.builder().build().serialize(combinedModel));
        var bundler = AwsServiceBundler.builder()
                .serviceName("dynamodb")
                .resolver(serviceName -> serializedModel)
                .allowedPrefixes(ApiStandardTerminology.getReadOnlyApiPrefixes())
                .blockedPrefixes(ApiStandardTerminology.getWriteApiPrefixes())
                .build();
        var bundle = bundler.bundle();
        var bundleModel = new ModelAssembler().addUnparsedModel("bundle.json", bundle.getModel())
                .disableValidation()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        assertThat(bundleModel.getServiceShapes()).hasSize(1);

        var serviceShape = bundleModel.getServiceShapes().iterator().next();

        assertThat(serviceShape.getId().toString()).isEqualTo(bundle.getServiceName());
        //We do not know which service gets picked because AwsServiceBundle chooses the first one it finds.
        String excludedNamespace;
        if (bundle.getServiceName().equals("com.amazonaws.accessanalyzer#AccessAnalyzer")) {
            excludedNamespace = "com.amazonaws.dynamodb";
        } else {
            excludedNamespace = "com.amazonaws.accessanalyzer";
        }

        assertThat(bundleModel.getShapeIds()).hasSizeLessThan(totalShapes)
                .filteredOn(i -> i.toString().startsWith(excludedNamespace))
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
