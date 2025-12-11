/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.server.ProxyOperationTrait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.modelbundle.api.model.AdditionalInput;
import software.amazon.smithy.modelbundle.api.model.ModelBundleVersion;
import software.amazon.smithy.modelbundle.api.model.SmithyBundle;

class ModelBundlesTest {

    @Test
    void testV2OperationWithNoInputGetsSyntheticAdditionalInputShape() {
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
                .modelBundleVersion(ModelBundleVersion.V2)
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
        assertTrue(trait.shouldUnwrapInput());
    }

    @Test
    void testV2OperationWithInputGetsWrapperWithOriginalInputMember() {
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
                .modelBundleVersion(ModelBundleVersion.V2)
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
        assertTrue(trait.shouldUnwrapInput());
    }

    @Test
    void testV1OperationWithNoInputUsesLegacyBehavior() {
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
                .modelBundleVersion(ModelBundleVersion.V1)
                .build();

        var model = ModelBundles.prepareModelForBundling(bundle);

        // Verify proxy operation exists
        ShapeId proxyOperationId = ShapeId.from("com.example#TestOperationProxy");
        assertTrue(model.getShape(proxyOperationId).isPresent());

        var proxyOperation = model.expectShape(proxyOperationId).asOperationShape().get();
        assertTrue(proxyOperation.getInput().isPresent());

        // Verify V1 uses synthetic container named after additionalInput shape
        ShapeId syntheticInputId = ShapeId.from("smithy.mcp#AdditionalInputForAdditionalInputData");
        assertEquals(syntheticInputId, proxyOperation.getInputShape());

        var syntheticInputShape = model.expectShape(syntheticInputId, StructureShape.class);
        // Only additionalInput member since original operation had no input
        assertEquals(1, syntheticInputShape.members().size());
        assertTrue(syntheticInputShape.getMember("additionalInput").isPresent());

        // Verify ProxyOperationTrait has correct metadata for V1
        var trait = proxyOperation.expectTrait(ProxyOperationTrait.class);
        assertEquals(ShapeId.from("com.example#TestOperation"), trait.getDelegateOperation());
        assertNull(trait.getInputMemberName());
        assertEquals("additionalInput", trait.getAdditionalInputMemberName());
        assertFalse(trait.shouldUnwrapInput());
    }

    @Test
    void testV1OperationWithInputMixesAdditionalInputIntoExistingShape() {
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
                .modelBundleVersion(ModelBundleVersion.V1)
                .build();

        var model = ModelBundles.prepareModelForBundling(bundle);

        // Verify proxy operation exists
        ShapeId proxyOperationId = ShapeId.from("com.example#GetUserProxy");
        assertTrue(model.getShape(proxyOperationId).isPresent());
        var proxyOperation = model.expectShape(proxyOperationId).asOperationShape().get();

        // Verify proxy has input - V1 uses modified input shape (with Proxy suffix)
        assertTrue(proxyOperation.getInput().isPresent());
        var inputShapeId = proxyOperation.getInputShape();
        assertEquals(ShapeId.from("com.example#GetUserInputProxy"), inputShapeId);

        var inputShape = model.expectShape(inputShapeId, StructureShape.class);

        // Should have 2 members: original userId and mixed-in additionalInput
        assertEquals(2, inputShape.members().size());

        // Should have original input member
        assertTrue(inputShape.getMember("userId").isPresent());

        // Should have member named "additionalInput" mixed in
        assertTrue(inputShape.getMember("additionalInput").isPresent());
        var additionalInputMember = inputShape.getMember("additionalInput").get();
        assertEquals(ShapeId.from("com.example.additional#AuthContext"), additionalInputMember.getTarget());

        // Verify ProxyOperationTrait has correct metadata for V1
        var trait = proxyOperation.expectTrait(ProxyOperationTrait.class);
        assertEquals(ShapeId.from("com.example#GetUser"), trait.getDelegateOperation());
        assertNull(trait.getInputMemberName());
        assertEquals("additionalInput", trait.getAdditionalInputMemberName());
        assertFalse(trait.shouldUnwrapInput());
    }

    @Test
    void testMissingVersionDefaultsToV1Behavior() {
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

        // Bundle without modelBundleVersion set - should default to V1
        SmithyBundle bundle = SmithyBundle.builder()
                .model(smithyModel)
                .serviceName("com.example#TestService")
                .additionalInput(additionalInput)
                .configType("configType")
                .config(Document.ofObject(null))
                .build();

        var model = ModelBundles.prepareModelForBundling(bundle);

        // Verify it uses V1 behavior (mixed-in input shape)
        ShapeId proxyOperationId = ShapeId.from("com.example#GetUserProxy");
        var proxyOperation = model.expectShape(proxyOperationId).asOperationShape().get();

        // V1 uses modified input shape (with Proxy suffix)
        var inputShapeId = proxyOperation.getInputShape();
        assertEquals(ShapeId.from("com.example#GetUserInputProxy"), inputShapeId);

        var inputShape = model.expectShape(inputShapeId, StructureShape.class);
        // Should have 2 members: original userId and mixed-in additionalInput
        assertEquals(2, inputShape.members().size());
        assertTrue(inputShape.getMember("userId").isPresent());
        assertTrue(inputShape.getMember("additionalInput").isPresent());

        // Verify ProxyOperationTrait has unwrapInput=false for V1
        var trait = proxyOperation.expectTrait(ProxyOperationTrait.class);
        assertFalse(trait.shouldUnwrapInput());
    }
}
