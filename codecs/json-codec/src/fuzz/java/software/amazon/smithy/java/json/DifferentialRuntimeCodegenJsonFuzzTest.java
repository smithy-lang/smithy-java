/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenException;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.ShapeUtils;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import software.amazon.smithy.model.shapes.ShapeType;
import software.smithy.fuzz.test.model.GeneratedSchemaIndex;

class DifferentialRuntimeCodegenJsonFuzzTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_CANONICAL_EXPONENT = 10_000;

    private static final JsonCodec INTERPRETED =
            JsonCodec.builder().overrideSerdeProvider(new SmithyJsonSerdeProvider()).build();
    private static final JsonCodec CODEGEN;

    static {
        // Avoid leaking strict mode to other fuzz tests.
        System.setProperty("smithy-java.runtime-codegen.json", "strict");
        try {
            CODEGEN = JsonCodec.builder()
                    .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                    .runtimeCodegen(true)
                    .build();
        } finally {
            System.clearProperty("smithy-java.runtime-codegen.json");
        }
    }

    @DictionaryFile(resourcePath = "/dictionary/codec-fuzz.dict")
    @MethodSource("seed")
    @FuzzTest
    void fuzzDifferential(byte[] input) {
        Assertions.assertTimeoutPreemptively(DEFAULT_TIMEOUT,
                () -> runTestsOn(input),
                () -> "Timeout with input: " + Base64.getEncoder().encodeToString(input));
    }

    private void runTestsOn(byte[] input) {
        boolean skipCanonicalSerialization = hasExcessiveJsonExponent(input);
        for (var shapeBuilderSupplier : getShapeBuilders()) {
            var refResult = tryDeserialize(INTERPRETED, shapeBuilderSupplier.get(), input);
            var genResult = tryDeserialize(CODEGEN, shapeBuilderSupplier.get(), input);

            if (refResult.error != null && genResult.error != null) {
                continue;
            }

            if (refResult.error == null && genResult.error != null) {
                Assertions.fail(String.format(
                        "Interpreted codec succeeded but codegen codec failed.%n"
                                + "Input (base64): %s%n"
                                + "Interpreted output: %s%n"
                                + "Codegen error: %s",
                        Base64.getEncoder().encodeToString(input),
                        safeSerializeToString(refResult.shape, skipCanonicalSerialization),
                        genResult.error));
            }

            if (refResult.error != null) {
                Assertions.fail(String.format(
                        "Codegen codec succeeded but interpreted codec failed.%n"
                                + "Input (base64): %s%n"
                                + "Codegen output: %s%n"
                                + "Interpreted error: %s",
                        Base64.getEncoder().encodeToString(input),
                        safeSerializeToString(genResult.shape, skipCanonicalSerialization),
                        refResult.error));
            }

            // TODO: Remove when NumberCodec.writeBigDecimal bounds plain-notation expansion.
            if (skipCanonicalSerialization) {
                continue;
            }

            byte[] refBytes = trySerialize(INTERPRETED, refResult.shape);
            byte[] genBytes = trySerialize(INTERPRETED, genResult.shape);
            if ((refBytes == null) != (genBytes == null)) {
                Assertions.fail(String.format(
                        "Serialization of the deserialized shape failed on one side only.%n"
                                + "Input (base64): %s%nInterpreted: %s%nCodegen: %s",
                        Base64.getEncoder().encodeToString(input),
                        asString(refBytes),
                        asString(genBytes)));
            }
            if (refBytes != null && !Arrays.equals(refBytes, genBytes)) {
                Assertions.fail(String.format(
                        "Deserialized values diverge.%n"
                                + "Input (base64): %s%n"
                                + "Interpreted: %s%n"
                                + "Codegen:     %s",
                        Base64.getEncoder().encodeToString(input),
                        asString(refBytes),
                        asString(genBytes)));
            }

            byte[] genSerialized;
            try {
                genSerialized = ByteBufferUtils.getBytes(CODEGEN.serialize(refResult.shape));
            } catch (RuntimeCodegenException e) {
                // Strict-mode failures are not input-dependent serialization failures.
                throw e;
            } catch (Exception e) {
                genSerialized = null;
            }
            if ((refBytes == null) != (genSerialized == null)
                    || (refBytes != null && !Arrays.equals(refBytes, genSerialized))) {
                Assertions.fail(String.format(
                        "Serializer divergence for the same shape.%n"
                                + "Input (base64): %s%n"
                                + "Interpreted: %s%n"
                                + "Codegen:     %s",
                        Base64.getEncoder().encodeToString(input),
                        asString(refBytes),
                        asString(genSerialized)));
            }
        }
    }

    private static String asString(byte[] bytes) {
        return bytes == null ? "<serialization failed>" : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] trySerialize(JsonCodec codec, SerializableShape shape) {
        try {
            return ByteBufferUtils.getBytes(codec.serialize(shape));
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeSerializeToString(SerializableShape shape, boolean skipCanonicalSerialization) {
        return skipCanonicalSerialization
                ? "<canonical serialization omitted: excessive exponent>"
                : asString(trySerialize(INTERPRETED, shape));
    }

    private static boolean hasExcessiveJsonExponent(byte[] input) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < input.length; i++) {
            int current = input[i] & 0xff;
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if ((current != 'e' && current != 'E')
                    || i == 0
                    || ((input[i - 1] < '0' || input[i - 1] > '9') && input[i - 1] != '.')) {
                continue;
            }

            int offset = i + 1;
            if (offset < input.length && (input[offset] == '+' || input[offset] == '-')) {
                offset++;
            }
            int magnitude = 0;
            int digits = 0;
            while (offset < input.length && input[offset] >= '0' && input[offset] <= '9') {
                digits++;
                int digit = input[offset++] - '0';
                if (magnitude > (MAX_CANONICAL_EXPONENT - digit) / 10) {
                    return true;
                }
                magnitude = magnitude * 10 + digit;
            }
            if (digits > 0 && magnitude > MAX_CANONICAL_EXPONENT) {
                return true;
            }
        }
        return false;
    }

    private record DeserializeResult(SerializableShape shape, Exception error) {}

    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    private static DeserializeResult tryDeserialize(JsonCodec codec, ShapeBuilder<?> builder, byte[] input) {
        try {
            return new DeserializeResult(codec.deserializeShape(input, builder), null);
        } catch (SerializationException
                | IllegalArgumentException
                | IllegalStateException
                | UnsupportedOperationException
                | IndexOutOfBoundsException
                | NullPointerException e) {
            return new DeserializeResult(null, e);
        }
    }

    private static Stream<byte[]> seed() {
        return getShapeBuilders().stream()
                .flatMap(b -> Stream.generate(() -> b).limit(10))
                .map(Supplier::get)
                .map(DifferentialRuntimeCodegenJsonFuzzTest::tryGenerateSeed)
                .filter(Objects::nonNull);
    }

    private static byte[] tryGenerateSeed(ShapeBuilder<?> builder) {
        try {
            return trySerialize(INTERPRETED, ShapeUtils.generateRandom(builder));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Supplier<ShapeBuilder<? extends SerializableShape>>> getShapeBuilders() {
        var schemaIndex = new GeneratedSchemaIndex();
        List<Supplier<ShapeBuilder<?>>> shapeBuilders = new ArrayList<>();
        schemaIndex.visit(s -> {
            if (s.type() == ShapeType.STRUCTURE || s.type() == ShapeType.UNION) {
                shapeBuilders.add(s::shapeBuilder);
            }
        });
        return shapeBuilders;
    }
}
