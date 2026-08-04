/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.compiler;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DynamicClientUsageChecker} using Error Prone's {@link CompilationTestHelper}, which compiles the
 * inline sources against the real {@code DynamicClient} with the check active and asserts on the diagnostics via
 * {@code // BUG: Diagnostic contains:} markers. This replaces the raw-plugin prototype's shell driver.
 */
class DynamicClientUsageCheckerTest {

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(DynamicClientUsageChecker.class, getClass());
    }

    // A single-line Smithy model embedded as a Java string constant (newlines escaped for the source under test).
    private static final String MODEL = String.join(
            "\\n",
            "$version: \\\"2\\\"",
            "namespace smithy.example",
            "service Sprockets { operations: [CreateSprocket, GetSprocket] }",
            "operation CreateSprocket { input := {} output := {} }",
            "operation GetSprocket { input := { id: String } output := { id: String } }");

    @Test
    void flagsUnknownOperation() {
        helper()
                .addSourceLines(
                        "Demo.java",
                        "import java.util.Map;",
                        "import software.amazon.smithy.java.dynamicclient.DynamicClient;",
                        "import software.amazon.smithy.model.Model;",
                        "import software.amazon.smithy.model.shapes.ShapeId;",
                        "class Demo {",
                        "  static final String MODEL = \"" + MODEL + "\";",
                        "  void run() {",
                        "    DynamicClient client = DynamicClient.builder()",
                        "        .serviceId(ShapeId.from(\"smithy.example#Sprockets\"))",
                        "        .model(Model.assembler().addUnparsedModel(\"demo.smithy\", MODEL).assemble().unwrap())",
                        "        .build();",
                        "    // BUG: Diagnostic contains: Operation 'GetSprokcet' not found",
                        "    client.call(\"GetSprokcet\", Map.of(\"id\", \"1\"));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void flagsUnknownInputMember() {
        helper()
                .addSourceLines(
                        "Demo.java",
                        "import java.util.Map;",
                        "import software.amazon.smithy.java.dynamicclient.DynamicClient;",
                        "import software.amazon.smithy.model.Model;",
                        "import software.amazon.smithy.model.shapes.ShapeId;",
                        "class Demo {",
                        "  static final String MODEL = \"" + MODEL + "\";",
                        "  void run() {",
                        "    DynamicClient client = DynamicClient.builder()",
                        "        .serviceId(ShapeId.from(\"smithy.example#Sprockets\"))",
                        "        .model(Model.assembler().addUnparsedModel(\"demo.smithy\", MODEL).assemble().unwrap())",
                        "        .build();",
                        "    // BUG: Diagnostic contains: 'idd' is not a member of input 'GetSprocketInput'",
                        "    client.call(\"GetSprocket\", Map.of(\"idd\", \"1\"));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void acceptsValidCalls() {
        helper()
                .addSourceLines(
                        "Demo.java",
                        "import java.util.Map;",
                        "import software.amazon.smithy.java.dynamicclient.DynamicClient;",
                        "import software.amazon.smithy.model.Model;",
                        "import software.amazon.smithy.model.shapes.ShapeId;",
                        "class Demo {",
                        "  static final String MODEL = \"" + MODEL + "\";",
                        "  void run() {",
                        "    DynamicClient client = DynamicClient.builder()",
                        "        .serviceId(ShapeId.from(\"smithy.example#Sprockets\"))",
                        "        .model(Model.assembler().addUnparsedModel(\"demo.smithy\", MODEL).assemble().unwrap())",
                        "        .build();",
                        "    client.call(\"GetSprocket\", Map.of(\"id\", \"1\"));",
                        "    client.call(\"CreateSprocket\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void detectsServiceWhenNoServiceIdGiven() {
        // Single service in the model: the checker auto-detects it, mirroring the runtime builder.
        helper()
                .addSourceLines(
                        "Demo.java",
                        "import java.util.Map;",
                        "import software.amazon.smithy.java.dynamicclient.DynamicClient;",
                        "import software.amazon.smithy.model.Model;",
                        "class Demo {",
                        "  static final String MODEL = \"" + MODEL + "\";",
                        "  void run() {",
                        "    DynamicClient client = DynamicClient.builder()",
                        "        .model(Model.assembler().addUnparsedModel(\"demo.smithy\", MODEL).assemble().unwrap())",
                        "        .build();",
                        "    // BUG: Diagnostic contains: Operation 'Nope' not found",
                        "    client.call(\"Nope\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void abstainsOnDynamicOperationName() {
        // Operation name comes from a parameter: the check must NOT flag it.
        helper()
                .addSourceLines(
                        "Demo.java",
                        "import java.util.Map;",
                        "import software.amazon.smithy.java.dynamicclient.DynamicClient;",
                        "import software.amazon.smithy.model.Model;",
                        "import software.amazon.smithy.model.shapes.ShapeId;",
                        "class Demo {",
                        "  static final String MODEL = \"" + MODEL + "\";",
                        "  void run(String op) {",
                        "    DynamicClient client = DynamicClient.builder()",
                        "        .serviceId(ShapeId.from(\"smithy.example#Sprockets\"))",
                        "        .model(Model.assembler().addUnparsedModel(\"demo.smithy\", MODEL).assemble().unwrap())",
                        "        .build();",
                        "    client.call(op, Map.of(\"id\", \"1\"));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void abstainsWhenModelNotStaticallyResolvable() {
        // Model is passed in as a parameter: nothing to resolve, so no flags even for a bogus operation.
        helper()
                .addSourceLines(
                        "Demo.java",
                        "import java.util.Map;",
                        "import software.amazon.smithy.java.dynamicclient.DynamicClient;",
                        "import software.amazon.smithy.model.Model;",
                        "import software.amazon.smithy.model.shapes.ShapeId;",
                        "class Demo {",
                        "  void run(Model model) {",
                        "    DynamicClient client = DynamicClient.builder()",
                        "        .serviceId(ShapeId.from(\"smithy.example#Sprockets\"))",
                        "        .model(model)",
                        "        .build();",
                        "    client.call(\"TotallyNotAnOperation\", Map.of(\"id\", \"1\"));",
                        "  }",
                        "}")
                .doTest();
    }
}
