/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentEqualsFlags;
import software.amazon.smithy.java.runtime.json.jackson.JacksonJsonSerdeProvider;

public class ParsingTest {
    @ParameterizedTest
    @MethodSource("parserTestCases")
    public void parserTestCases(JsonSerdeProvider provider, Path file) throws IOException {
        var filename = file.getFileName().toString();
        var contents = Files.readAllBytes(file);
        var codec = JsonCodec.builder().overrideSerdeProvider(provider).build();

        if (filename.startsWith("y_")) {
            try (var deser = codec.createDeserializer(contents)) {
                var doc = deser.readDocument();
                if (doc != null) {
                    var str = codec.serializeToString(doc);
                    var doc2 = codec.createDeserializer(str.getBytes(StandardCharsets.UTF_8)).readDocument();
                    if (!Document.equals(doc2, doc, DocumentEqualsFlags.NUMBER_PROMOTION)) {
                        assertThat(doc2, equalTo(doc));
                    }
                }
            }
        } else if (filename.startsWith("n_")) {
            Assertions.assertThrows(SerializationException.class, () -> {
                try (var deser = codec.createDeserializer(contents)) {
                    deser.readDocument();
                }
            }, "Expected SerializationError for `" + file + "`");
        } else if (filename.startsWith("i_")) {
            // Make sure that if there are errors, they're all SerializationExceptions.
            try (var deser = codec.createDeserializer(contents)) {
                deser.readDocument();
            } catch (SerializationException e) {
                System.out.println("i test failed " + filename + ": " + e.getMessage());
            }
        }
    }

    static List<Arguments> parserTestCases() throws IOException, URISyntaxException {
        var jacksonProvider = new JacksonJsonSerdeProvider();

        List<Arguments> arguments = new ArrayList<>();
        for (var path : loadJsonFiles()) {
            arguments.add(Arguments.arguments(jacksonProvider, path));
        }

        return arguments;
    }

    public static List<Path> loadJsonFiles() throws IOException, URISyntaxException {
        List<Path> files = new ArrayList<>();
        var resource = Objects.requireNonNull(
            JsonSerializerTest.class.getResource("tests/test_parsing"),
            "no test cases"
        );
        Path dir = Paths.get(resource.toURI());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        return files;
    }
}
