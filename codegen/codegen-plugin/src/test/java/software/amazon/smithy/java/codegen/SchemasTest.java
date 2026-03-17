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
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

public class SchemasTest {

    private final MockManifest manifest = new MockManifest();

    @Test
    void largeNumberOfSchemasTest() {

        int totalOperations = 250;
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

        // Verify multiple Schema partition files were generated
        assertThat(schemaFiles)
                .hasSizeGreaterThanOrEqualTo(2)
                .contains("Schemas.java")
                .allMatch(s -> s.matches("Schemas\\d*\\.java"));

        // Verify total schema count across all partition files matches expected
        long totalSchemas = schemaFiles.stream()
                .mapToLong(s -> Pattern.compile("static final Schema ")
                        .matcher(getFileString(s))
                        .results()
                        .count())
                .sum();
        assertThat(totalSchemas).isEqualTo(totalOperations * 2L);

        // Verify schema references use $N pattern
        verifySchemaReference("Op001Input");
        verifySchemaReference("Op100Output");
    }

    private void verifySchemaReference(String structureName) {
        assertThat(getFileString(structureName + ".java"))
                .containsPattern("public static final Schema \\$SCHEMA = Schemas\\d*\\.[A-Z][A-Z0-9_]+");
    }

    private String getFileString(String fileName) {
        var fileStringOptional = manifest.getFileString(
                Paths.get(String.format("/java/t/s/c/model/%s", fileName)));
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
