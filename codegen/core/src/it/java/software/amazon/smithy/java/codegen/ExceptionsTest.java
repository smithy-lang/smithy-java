/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.smithy.codegen.test.model.EmptyException;
import io.smithy.codegen.test.model.ExceptionWithExtraStringException;
import io.smithy.codegen.test.model.SimpleException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

public class ExceptionsTest {
    static Stream<SerializableShape> exceptions() {
        return Stream.of(
            SimpleException.builder().message("OOOPS!").build(),
            ExceptionWithExtraStringException.builder().message("whoopsy").extra("daisy").build(),
            EmptyException.builder().build()
        );
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void simpleExceptionToDocumentRoundTrip(ModeledApiException exception) {
        var output = Utils.pojoToDocumentRoundTrip(exception);
        assertEquals(exception.getMessage(), output.getMessage());
        assertEquals(exception.getCause(), output.getCause());
        assertNotEquals(exception.hashCode(), output.hashCode());
    }
}
