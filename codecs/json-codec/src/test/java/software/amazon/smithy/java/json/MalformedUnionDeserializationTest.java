/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.json.bench.model.BenchUnion;
import software.amazon.smithy.java.json.bench.model.ComplexStruct;

/**
 * Malformed union payloads (no member set, or more than one member set) must surface as
 * {@link SerializationException} regardless of the JSON provider, so that callers can map them to
 * client errors (e.g. HTTP 400). The union builder itself throws {@link NullPointerException} /
 * {@link IllegalArgumentException} for these cases, which must not escape the codec.
 */
public class MalformedUnionDeserializationTest extends ProviderTestBase {

    private static final String COMPLEX_PREFIX = "{\"id\":\"x\",\"count\":1,\"enabled\":true,\"choice\":";

    static List<Named<Payload<?>>> payloads() {
        return List.of(
                Named.of("nested union with no members",
                        new Payload<>(COMPLEX_PREFIX + "{}}", ComplexStruct::builder)),
                Named.of("nested union with two members",
                        new Payload<>(COMPLEX_PREFIX + "{\"stringValue\":\"a\",\"intValue\":1}}",
                                ComplexStruct::builder)),
                Named.of("top-level union with no members",
                        new Payload<>("{}", BenchUnion::builder)),
                Named.of("top-level union with two members",
                        new Payload<>("{\"stringValue\":\"a\",\"intValue\":1}", BenchUnion::builder)));
    }

    static Stream<Arguments> malformedUnions() {
        var payloads = payloads();
        return providers().stream()
                .flatMap(provider -> payloads.stream()
                        .map(payload -> Arguments.of(provider.get()[0], payload)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource
    void malformedUnions(JsonSerdeProvider provider, Payload<?> payload) {
        assertThrowsSerializationException(codec(provider), payload);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloads")
    void malformedUnionsWithRuntimeCodegen(Payload<?> payload) {
        assertThrowsSerializationException(
                JsonCodec.builder().overrideSerdeProvider(SMITHY).runtimeCodegen(true).build(),
                payload);
    }

    private static void assertThrowsSerializationException(JsonCodec codec, Payload<?> payload) {
        byte[] bytes = payload.json().getBytes(StandardCharsets.UTF_8);
        try (codec) {
            // JsonCodec overrides both overloads, so each one needs to be checked.
            assertSerializationException(payload,
                    () -> codec.deserializeShape(bytes, payload.builder().get()));
            assertSerializationException(payload,
                    () -> codec.deserializeShape(ByteBuffer.wrap(bytes), payload.builder().get()));
        }
    }

    private static void assertSerializationException(Payload<?> payload, Executable executable) {
        var e = assertThrows(Throwable.class, executable);
        assertThat(e)
                .as("Expected SerializationException for %s but got %s", payload.json(), e)
                .isInstanceOf(SerializationException.class);
    }

    record Payload<T extends SerializableShape>(String json, Supplier<ShapeBuilder<T>> builder) {}
}
