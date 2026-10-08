/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import software.amazon.smithy.model.shapes.ShapeType;
import software.smithy.fuzz.test.model.GeneratedSchemaIndex;

class DifferentialRuntimeCodegenCborFuzzTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final Rpcv2CborCodec INTERPRETED = Rpcv2CborCodec.builder().runtimeCodegen(false).build();
    private static final Rpcv2CborCodec CODEGEN;

    static {
        // CBOR resolves strictness from the property at codec-generation time rather than at codec
        // construction, so the property must stay set for the lifetime of the JVM. The fuzz task
        // forks a fresh JVM per test class, so this cannot leak into other fuzz tests; the
        // interpreted codec is immune because an explicit runtimeCodegen(false) wins over it.
        System.setProperty("smithy-java.runtime-codegen.cbor", "strict");
        CODEGEN = Rpcv2CborCodec.builder().runtimeCodegen(true).build();
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
                        asString(trySerialize(INTERPRETED, refResult.shape)),
                        genResult.error));
            }

            if (refResult.error != null) {
                Assertions.fail(String.format(
                        "Codegen codec succeeded but interpreted codec failed.%n"
                                + "Input (base64): %s%n"
                                + "Codegen output: %s%n"
                                + "Interpreted error: %s",
                        Base64.getEncoder().encodeToString(input),
                        asString(trySerialize(INTERPRETED, genResult.shape)),
                        refResult.error));
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
        return bytes == null ? "<serialization failed>" : Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] trySerialize(Rpcv2CborCodec codec, SerializableShape shape) {
        try {
            return ByteBufferUtils.getBytes(codec.serialize(shape));
        } catch (Exception e) {
            return null;
        }
    }

    private record DeserializeResult(SerializableShape shape, Exception error) {}

    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    private static DeserializeResult tryDeserialize(Rpcv2CborCodec codec, ShapeBuilder<?> builder, byte[] input) {
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
                .map(DifferentialRuntimeCodegenCborFuzzTest::tryGenerateSeed)
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
