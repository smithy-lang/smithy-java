/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.fuzz;

import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.ArrayList;
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class CodecDeserializationFuzzTestBase {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Main fuzz test method that Jazzer invokes.
     * Fuzzes deserialization with various types and inputs.
     */
    @DictionaryFile(resourcePath = "/dictionary/codec-fuzz.dict")
    @MethodSource("seed")
    @FuzzTest
    public final void fuzzDeserializer(byte[] input) {
        Assertions.assertTimeoutPreemptively(DEFAULT_TIMEOUT,
                () -> runTestsOn(input),
                String.format("Timeout on with input: %s",
                        Base64.getEncoder().encodeToString(input)));
    }

    protected final void runTestsOn(byte[] input) {
        var shapeBuilders = getShapeBuilders();
        var codec = this.codecToFuzz();
        for (var shapeBuilder : shapeBuilders) {
            try {
                shapeBuilder.get().deserialize(codec.createDeserializer(input)).build();
            } catch (Exception e) {
                if (!isErrorAcceptable(e)) {
                    var message = "Got an exception for Input : [%s] on shape [%s]"
                            .formatted(Base64.getEncoder().encodeToString(input), shapeBuilder.get().schema().id());
                    throw new RuntimeException(message, e);
                }
            }
        }
    }

    private Stream<byte[]> seed() {
        var shapeBuilders = getShapeBuilders();
        var codec = this.codecToFuzz();
        return shapeBuilders.stream()
                .flatMap(b -> Stream.generate(() -> b).limit(10))
                .map(Supplier::get)
                .map(ShapeUtils::generateRandom)
                .map(codec::serialize)
                .map(ByteBufferUtils::getBytes);
    }

    /**
     * Returns the codec to fuzz.
     */
    protected abstract Codec codecToFuzz();

    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    protected boolean isErrorAcceptable(Exception exception) {
        try {
            throw exception;
        } catch (NullPointerException npe) {
            return switch (npe.getMessage()) {
                case "no union value set" -> true;
                case null, default -> false;
            };
        } catch (SerializationException
                | IllegalArgumentException
                | IllegalStateException
                | UnsupportedOperationException
                | IndexOutOfBoundsException ignored) {
            return true;
        } catch (Exception e) {
            return false;
        }
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
