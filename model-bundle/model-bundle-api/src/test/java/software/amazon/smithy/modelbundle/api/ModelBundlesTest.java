/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.modelbundle.api.model.AdditionalInput;
import software.amazon.smithy.modelbundle.api.model.SmithyBundle;

class ModelBundlesTest {

    @Test
    void testOperationWithNoInputGetsSyntheticAdditionalInputShape() {
        String smithyModel = """
                $version: "2.0"

                namespace com.example

                service TestService {
                    version: "1.0"
                    operations: [TestOperation]
                }

                operation TestOperation {
                    output: TestOutput
                }

                structure TestOutput {
                    result: String
                }
                """;

        String additionalInputModel = """
                $version: "2.0"

                namespace com.example.additional

                structure AdditionalInputData {
                    context: String
                }
                """;

        AdditionalInput additionalInput = AdditionalInput.builder()
                .identifier("com.example.additional#AdditionalInputData")
                .model(additionalInputModel)
                .build();

        SmithyBundle bundle = SmithyBundle.builder()
                .model(smithyModel)
                .serviceName("com.example#TestService")
                .additionalInput(additionalInput)
                .configType("configType")
                .config(Document.ofObject(null))
                .build();

        var model = ModelBundles.prepareModelForBundling(bundle);

        ShapeId syntheticInputId = ShapeId.from("smithy.mcp#AdditionalInputForAdditionalInputData");
        assertTrue(model.getShape(syntheticInputId).isPresent());
        var syntheticInputShape = model.expectShape(syntheticInputId, StructureShape.class);
        assertEquals(1, syntheticInputShape.members().size());
        assertTrue(syntheticInputShape.getMember("additionalInput").isPresent());
        var additionalInputMember = syntheticInputShape.getMember("additionalInput").get();
        assertEquals(ShapeId.from("com.example.additional#AdditionalInputData"),
                additionalInputMember.getTarget());

        ShapeId proxyOperationId = ShapeId.from("com.example#TestOperationProxy");
        assertTrue(model.getShape(proxyOperationId).isPresent());

        var proxyOperation = model.expectShape(proxyOperationId).asOperationShape().get();
        assertTrue(proxyOperation.getInput().isPresent());

        assertEquals(syntheticInputId, proxyOperation.getInputShape());
    }
}
