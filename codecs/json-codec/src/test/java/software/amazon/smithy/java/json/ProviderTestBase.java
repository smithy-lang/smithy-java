/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.util.List;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;
import software.amazon.smithy.java.json.bench.model.*;
import software.amazon.smithy.java.json.codegen.ClassFileSpecializedJsonSerde;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

abstract class ProviderTestBase {

    static final JacksonJsonSerdeProvider JACKSON = new JacksonJsonSerdeProvider();
    static final SmithyJsonSerdeProvider SMITHY = new SmithyJsonSerdeProvider();
    static final ClassFileSpecializedJsonSerde CODEGEN_SERDE = new ClassFileSpecializedJsonSerde();
    static final CodegenJsonSerdeProvider CODEGEN = new CodegenJsonSerdeProvider(SMITHY, CODEGEN_SERDE);

    static {
        for (var entry : List.of(
                new Class<?>[] {SimpleStruct.class},
                new Class<?>[] {ComplexStruct.class},
                new Class<?>[] {NumericStruct.class},
                new Class<?>[] {StringStruct.class},
                new Class<?>[] {TimestampStruct.class},
                new Class<?>[] {BlobStruct.class},
                new Class<?>[] {RecursiveStruct.class},
                new Class<?>[] {AllListsStruct.class},
                new Class<?>[] {JsonNameStruct.class},
                new Class<?>[] {NestedStruct.class},
                new Class<?>[] {InnerStruct.class},
                new Class<?>[] {BenchUnion.class})) {
            Class<?> cls = entry[0];
            try {
                var schema = (software.amazon.smithy.java.core.schema.Schema) cls.getField("$SCHEMA").get(null);
                CODEGEN_SERDE.warmup(schema, cls);
            } catch (Exception ignored) {}
        }
    }

    static List<Arguments> providers() {
        return List.of(
                Arguments.of(Named.of("jackson", JACKSON)),
                Arguments.of(Named.of("smithy", SMITHY)),
                Arguments.of(Named.of("codegen", CODEGEN)));
    }

    static List<Arguments> crossProviders() {
        return List.of(
                Arguments.of(Named.of("jackson->jackson", JACKSON), JACKSON),
                Arguments.of(Named.of("smithy->smithy", SMITHY), SMITHY),
                Arguments.of(Named.of("jackson->smithy", JACKSON), SMITHY),
                Arguments.of(Named.of("smithy->jackson", SMITHY), JACKSON),
                Arguments.of(Named.of("codegen->codegen", CODEGEN), CODEGEN),
                Arguments.of(Named.of("codegen->smithy", CODEGEN), SMITHY),
                Arguments.of(Named.of("smithy->codegen", SMITHY), CODEGEN),
                Arguments.of(Named.of("codegen->jackson", CODEGEN), JACKSON),
                Arguments.of(Named.of("jackson->codegen", JACKSON), CODEGEN));
    }

    /**
     * Creates a codec with default settings using the given provider.
     */
    static JsonCodec codec(JsonSerdeProvider provider) {
        return JsonCodec.builder().overrideSerdeProvider(provider).build();
    }

    /**
     * Creates a codec with custom settings using the given provider.
     */
    static JsonCodec.Builder codecBuilder(JsonSerdeProvider provider) {
        return JsonCodec.builder().overrideSerdeProvider(provider);
    }
}
