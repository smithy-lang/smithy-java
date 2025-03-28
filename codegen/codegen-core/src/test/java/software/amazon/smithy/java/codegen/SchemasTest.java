/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.utils.TestJavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

public class SchemasTest {

    private final MockManifest manifest = new MockManifest();

    @Test
    void largeNumberOfSchemasTest() {

        int totalOperations = TestJavaCodegen.SCHEMA_PARTITION_THRESHOLD + 12; //Add some random number
        var smithyDefinition = new StringBuilder("""
                $version: "2"
                namespace s.j
                """);
        var serviceDefintion = new StringBuilder("""
                service TestService {
                    operations: [
                """);
        for (int i = 1; i <= totalOperations; i++) {
            String operationName = "Op%03d".formatted(i);
            serviceDefintion.append(operationName).append(",");
            smithyDefinition.append("""
                    operation %s {
                        input: %sInput,
                        output: %sOutput,
                    }
                    structure %sInput {
                        value: String
                    }
                    structure %sOutput {
                        value: String
                    }
                    """.formatted(operationName, operationName, operationName, operationName, operationName));
        }
        smithyDefinition.append(serviceDefintion.append("]}"));
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", smithyDefinition.toString())
                .disableValidation()
                .assemble()
                .unwrap();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(settings())
                .model(model)
                .build();
        new TestJavaCodegenPlugin().execute(context);
        var files = manifest.getFiles();
        var schemaFiles =
                files.stream().map(Path::getFileName).map(Path::toString).filter(s -> s.startsWith("Schema")).toList();

        //Verify each Schema class has no more 100 constants.
        assertThat(schemaFiles)
                .hasSize(3)
                .containsExactlyInAnyOrder("Schemas.java", "Schemas1.java", "Schemas2.java")
                .allSatisfy(s -> {
                    var content = getFileString(s);
                    long numberOfSchemas =
                            Pattern.compile("static final Schema ").matcher(content).results().count();
                    if ("Schemas2.java".equals(s)) {
                        assertThat(numberOfSchemas).isLessThan(100);
                    } else {
                        assertThat(numberOfSchemas).isEqualTo(100);
                    }
                });

        //Verify mappings are correct in structure classes
        verifySchemaReference("Op001Input", "Schemas");
        verifySchemaReference("Op060Input", "Schemas1");
        verifySchemaReference("Op104Output", "Schemas2");
    }

    private void verifySchemaReference(String structureName, String expectedSchema) {
        assertThat(getFileString(structureName + ".java"))
                .contains("public static final Schema $SCHEMA = %s.%s".formatted(expectedSchema,
                        CodegenUtils.toUpperSnakeCase(structureName)));
    }

    private String getFileString(String fileName) {
        var fileStringOptional = manifest.getFileString(
                Paths.get(String.format("/t/s/c/model/%s", fileName)));
        assertThat(fileStringOptional).isPresent();
        return fileStringOptional.get();
    }

    private ObjectNode settings() {
        return ObjectNode.builder()
                .withMember("service", "s.j#TestService")
                .withMember("namespace", "t.s.c")
                .build();
    }

}
