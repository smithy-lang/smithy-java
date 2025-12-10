/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.server.ProxyOperationTrait;
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

        // Verify proxy operation exists
        ShapeId proxyOperationId = ShapeId.from("com.example#TestOperationProxy");
        assertTrue(model.getShape(proxyOperationId).isPresent());

        var proxyOperation = model.expectShape(proxyOperationId).asOperationShape().get();
        assertTrue(proxyOperation.getInput().isPresent());

        // Verify wrapper structure (named after operation)
        ShapeId syntheticInputId = ShapeId.from("com.example#TestOperationProxyInput");
        assertEquals(syntheticInputId, proxyOperation.getInputShape());

        var syntheticInputShape = model.expectShape(syntheticInputId, StructureShape.class);
        // Only additionalInput member since original operation had no input
        assertEquals(1, syntheticInputShape.members().size());
        assertTrue(syntheticInputShape.getMember("additionalInput").isPresent());
        var additionalInputMember = syntheticInputShape.getMember("additionalInput").get();
        assertEquals(ShapeId.from("com.example.additional#AdditionalInputData"),
                additionalInputMember.getTarget());

        // Verify ProxyOperationTrait has correct metadata for no-input operation
        var trait = proxyOperation.expectTrait(ProxyOperationTrait.class);
        assertEquals(ShapeId.from("com.example#TestOperation"), trait.getDelegateOperation());
        assertNull(trait.getInputMemberName());
        assertEquals("additionalInput", trait.getAdditionalInputMemberName());
    }

    @Test
    void testOperationWithInputGetsWrapperWithOriginalInputMember() {
        String smithyModel = """
                $version: "2.0"

                namespace com.example

                service TestService {
                    version: "1.0"
                    operations: [GetUser]
                }

                operation GetUser {
                    input: GetUserInput
                    output: GetUserOutput
                }

                structure GetUserInput {
                    userId: String
                }

                structure GetUserOutput {
                    name: String
                }
                """;

        String additionalInputModel = """
                $version: "2.0"

                namespace com.example.additional

                structure AuthContext {
                    token: String
                }
                """;

        AdditionalInput additionalInput = AdditionalInput.builder()
                .identifier("com.example.additional#AuthContext")
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

        // Verify proxy operation exists
        ShapeId proxyOperationId = ShapeId.from("com.example#GetUserProxy");
        assertTrue(model.getShape(proxyOperationId).isPresent());
        var proxyOperation = model.expectShape(proxyOperationId).asOperationShape().get();

        // Verify proxy has input
        assertTrue(proxyOperation.getInput().isPresent());

        // Verify wrapper structure (named after operation)
        var wrapperShapeId = proxyOperation.getInputShape();
        assertEquals(ShapeId.from("com.example#GetUserProxyInput"), wrapperShapeId);
        var wrapperShape = model.expectShape(wrapperShapeId, StructureShape.class);

        // Should have exactly 2 members
        assertEquals(2, wrapperShape.members().size());

        // Should have member named "getUserInput" (lowercased) pointing to original input
        assertTrue(wrapperShape.getMember("getUserInput").isPresent());
        var originalInputMember = wrapperShape.getMember("getUserInput").get();
        assertEquals(ShapeId.from("com.example#GetUserInput"), originalInputMember.getTarget());

        // Should have member named "additionalInput"
        assertTrue(wrapperShape.getMember("additionalInput").isPresent());
        var additionalInputMember = wrapperShape.getMember("additionalInput").get();
        assertEquals(ShapeId.from("com.example.additional#AuthContext"), additionalInputMember.getTarget());

        // Verify ProxyOperationTrait has correct metadata
        var trait = proxyOperation.expectTrait(ProxyOperationTrait.class);
        assertEquals(ShapeId.from("com.example#GetUser"), trait.getDelegateOperation());
        assertEquals("getUserInput", trait.getInputMemberName());
        assertEquals("additionalInput", trait.getAdditionalInputMemberName());
    }
}
