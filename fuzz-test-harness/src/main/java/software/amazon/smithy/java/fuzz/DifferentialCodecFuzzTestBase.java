/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.fuzz;

import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.ShapeUtils;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.shapes.ShapeType;
import software.smithy.fuzz.test.model.GeneratedSchemaIndex;

/**
 * Base class for differential fuzz testing between a reference codec and a test codec.
 *
 * <p>For each fuzz input, this base class deserializes through both codecs using every available
 * shape builder and compares the results. The four possible outcomes are:
 * <ul>
 *     <li>Both succeed with equal output — pass</li>
 *     <li>Both fail — pass (both strict on this input)</li>
 *     <li>Reference succeeds, test fails — check {@link #isAcceptableTestFailure}</li>
 *     <li>Test succeeds, reference fails — check {@link #isAcceptableReferenceFailure}</li>
 *     <li>Both succeed with different output — check {@link #isAcceptableDivergence}</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DifferentialCodecFuzzTestBase {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Returns the reference codec (the "known good" implementation, e.g. Jackson).
     */
    protected abstract Codec referenceCodec();

    /**
     * Returns the test codec (the implementation under test, e.g. Smithy native).
     */
    protected abstract Codec testCodec();

    @DictionaryFile(resourcePath = "/dictionary/codec-fuzz.dict")
    @MethodSource("seed")
    @FuzzTest
    public final void fuzzDifferential(byte[] input) {
        Assertions.assertTimeoutPreemptively(DEFAULT_TIMEOUT,
                () -> runTestsOn(input),
                String.format("Timeout with input: %s",
                        Base64.getEncoder().encodeToString(input)));
    }

    protected final void runTestsOn(byte[] input) {
        var shapeBuilders = getShapeBuilders();
        var refCodec = referenceCodec();
        var testCodec = testCodec();

        for (var shapeBuilderSupplier : shapeBuilders) {
            var refResult = tryDeserialize(refCodec, shapeBuilderSupplier.get(), input);
            var testResult = tryDeserialize(testCodec, shapeBuilderSupplier.get(), input);

            if (refResult.error != null && testResult.error != null) {
                // Both fail — acceptable
                continue;
            }

            if (refResult.error == null && testResult.error == null) {
                // Both succeed — compare serialized output through the reference codec.
                // Serialization itself may fail (e.g., unions with unknown members), which
                // is acceptable as long as both fail the same way.
                byte[] refBytes = trySerialize(refCodec, refResult.shape);
                byte[] testBytes = trySerialize(refCodec, testResult.shape);
                if (refBytes == null || testBytes == null) {
                    // Serialization failed for one or both — not a deserialization divergence
                    continue;
                }
                if (!Arrays.equals(refBytes, testBytes)) {
                    if (!isAcceptableDivergence(refResult.shape, testResult.shape, input)) {
                        Assertions.fail(String.format(
                                "Both codecs succeeded but produced different output.%n"
                                        + "Input (base64): %s%n"
                                        + "Reference output: %s%n"
                                        + "Test output:      %s",
                                Base64.getEncoder().encodeToString(input),
                                new String(refBytes, StandardCharsets.UTF_8),
                                new String(testBytes, StandardCharsets.UTF_8)));
                    }
                }
                continue;
            }

            if (refResult.error == null) {
                // Reference succeeded, test failed
                if (!isAcceptableTestFailure(refResult.shape, testResult.error, input)) {
                    Assertions.fail(String.format(
                            "Reference codec succeeded but test codec failed.%n"
                                    + "Input (base64): %s%n"
                                    + "Reference output: %s%n"
                                    + "Test error: %s",
                            Base64.getEncoder().encodeToString(input),
                            safeSerializeToString(refCodec, refResult.shape),
                            testResult.error));
                }
            } else {
                // Test succeeded, reference failed
                if (!isAcceptableReferenceFailure(testResult.shape, refResult.error, input)) {
                    Assertions.fail(String.format(
                            "Test codec succeeded but reference codec failed.%n"
                                    + "Input (base64): %s%n"
                                    + "Test output: %s%n"
                                    + "Reference error: %s",
                            Base64.getEncoder().encodeToString(input),
                            safeSerializeToString(testCodec, testResult.shape),
                            refResult.error));
                }
            }
        }
    }

    private static byte[] trySerialize(Codec codec, SerializableShape shape) {
        try {
            return ByteBufferUtils.getBytes(codec.serialize(shape));
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeSerializeToString(Codec codec, SerializableShape shape) {
        byte[] bytes = trySerialize(codec, shape);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : "<serialization failed>";
    }

    private record DeserializeResult(SerializableShape shape, Exception error) {}

    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    private DeserializeResult tryDeserialize(Codec codec, ShapeBuilder<?> builder, byte[] input) {
        try {
            var shape = builder.deserialize(codec.createDeserializer(input)).errorCorrection().build();
            return new DeserializeResult(shape, null);
        } catch (SerializationException
                | IllegalArgumentException
                | IllegalStateException
                | UnsupportedOperationException
                | IndexOutOfBoundsException
                | NullPointerException e) {
            return new DeserializeResult(null, e);
        }
    }

    private Stream<byte[]> seed() {
        var shapeBuilders = getShapeBuilders();
        var codec = referenceCodec();
        return shapeBuilders.stream()
                .flatMap(b -> Stream.generate(() -> b).limit(10))
                .map(Supplier::get)
                .map(ShapeUtils::generateRandom)
                .map(codec::serialize)
                .map(ByteBufferUtils::getBytes);
    }

    /**
     * Called when both codecs succeed but produce different serialized output.
     *
     * <p>Override to document known acceptable divergences (e.g., number type differences).
     *
     * @param referenceShape the shape produced by the reference codec
     * @param testShape the shape produced by the test codec
     * @param input the raw fuzz input bytes
     * @return true if the divergence is acceptable
     */
    protected boolean isAcceptableDivergence(
            SerializableShape referenceShape,
            SerializableShape testShape,
            byte[] input
    ) {
        return false;
    }

    /**
     * Called when the reference codec succeeds but the test codec fails.
     *
     * <p>Override to document cases where the test codec is intentionally stricter.
     *
     * @param referenceShape the shape produced by the reference codec
     * @param testError the error thrown by the test codec
     * @param input the raw fuzz input bytes
     * @return true if the test failure is acceptable
     */
    protected boolean isAcceptableTestFailure(SerializableShape referenceShape, Exception testError, byte[] input) {
        return false;
    }

    /**
     * Called when the test codec succeeds but the reference codec fails.
     *
     * <p>Override to document cases where the reference codec is intentionally stricter.
     *
     * @param testShape the shape produced by the test codec
     * @param referenceError the error thrown by the reference codec
     * @param input the raw fuzz input bytes
     * @return true if the reference failure is acceptable
     */
    protected boolean isAcceptableReferenceFailure(
            SerializableShape testShape,
            Exception referenceError,
            byte[] input
    ) {
        return false;
    }

    private List<Supplier<ShapeBuilder<? extends SerializableShape>>> getShapeBuilders() {
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
