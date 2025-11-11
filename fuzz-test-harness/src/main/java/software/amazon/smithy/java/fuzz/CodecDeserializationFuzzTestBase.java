/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.model.shapes.ShapeType;
import software.smithy.fuzz.test.model.GeneratedSchemaIndex;

public abstract class CodecDeserializationFuzzTestBase {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Main fuzz test method that Jazzer invokes.
     * Fuzzes deserialization with various types and inputs.
     */
    @FuzzTest
    public final void fuzzDeserializer(FuzzedDataProvider provider) {
        var codec = this.codecToFuzz();

        var input = provider.consumeRemainingAsBytes();

        var schemaIndex = new GeneratedSchemaIndex();
        Set<ShapeBuilder<?>> shapeBuilders = new HashSet<>();
        schemaIndex.visit(s -> {
            if (s.type() == ShapeType.STRUCTURE || s.type() == ShapeType.UNION) {
                shapeBuilders.add(s.shapeBuilder());
            }
        });
        Assertions.assertTimeoutPreemptively(DEFAULT_TIMEOUT, () -> {
            try {
                for (var shapeBuilder : shapeBuilders) {
                    shapeBuilder.deserialize(codec.createDeserializer(input));
                }
            } catch (Exception e) {
                isErrorAcceptable(e);
            }
        },
                String.format("Timeout on with input: %s",
                        Base64.getEncoder().encodeToString(input)));
    }

    protected abstract Codec codecToFuzz();

    protected boolean isErrorAcceptable(Exception exception) throws Exception {
        try {
            throw exception;
        } catch (SerializationException
                | IllegalArgumentException
                | IllegalStateException
                | UnsupportedOperationException
                | IndexOutOfBoundsException ignored) {
            return true;
        }
    }
}
