/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.util.List;
import org.junit.jupiter.params.provider.Arguments;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

/**
 * Base class for JSON codec tests that should run against both Jackson and smithy-native providers.
 *
 * <p>Subclasses use {@code @MethodSource("providers")} on parameterized tests to receive
 * a {@link JsonSerdeProvider} for each provider implementation.
 */
abstract class ProviderTestBase {

    static final JacksonJsonSerdeProvider JACKSON = new JacksonJsonSerdeProvider();
    static final SmithyJsonSerdeProvider SMITHY = new SmithyJsonSerdeProvider();

    static List<Arguments> providers() {
        return List.of(
                Arguments.of(JACKSON),
                Arguments.of(SMITHY));
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
