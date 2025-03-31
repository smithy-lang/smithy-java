/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.model.EmptyException;
import software.amazon.smithy.java.codegen.test.model.ExceptionWithExtraStringException;
import software.amazon.smithy.java.codegen.test.model.OptionalMessageException;
import software.amazon.smithy.java.codegen.test.model.SimpleException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableShape;

@ExtendWith(ReloadClassesExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExceptionsTest {
    static Stream<SerializableShape> exceptions() {
        return Stream.of(
                SimpleException.builder().message("OOOPS!").build(),
                ExceptionWithExtraStringException.builder().message("whoopsy").extra("daisy").build(),
                EmptyException.builder().build(),
                OptionalMessageException.builder().build(),
                OptionalMessageException.builder().message("optional").build());
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    @Order(1)
    void simpleExceptionToDocumentRoundTrip(ModeledException exception) {
        var output = Utils.pojoToDocumentRoundTrip(exception);
        assertEquals(exception.getMessage(), output.getMessage());
        assertEquals(exception.getCause(), output.getCause());
        assertNotEquals(exception.hashCode(), output.hashCode());
    }

    @Test
    @Order(2)
    void exceptionWithExplicitStackTraceCapture() {
        var cause = getCauseThrowable();
        var exception = SimpleException.builder().withCause(cause).withStackTrace().message("OOOPS!").build();
        assertThat(exception)
                .hasStackTraceContaining("exceptionWithExplicitStackTraceCapture")
                .hasStackTraceContaining("getCauseThrowable")
                .hasCause(cause);
    }

    @Test
    @Order(3)
    void defaultExceptionHasNoStackTrace() {
        var exception = SimpleException.builder().message("OOOPS!").build();
        assertThat(exception.getStackTrace()).isEmpty();
    }

    @Test
    @Order(4)
    @ReloadClasses
    void explicitDisableStackTraceCapture() {
        enableGlobalStackTraceCapture();
        try {
            var exception = SimpleException.builder().message("OOOPS!").build();
            assertThat(exception)
                    .hasStackTraceContaining("explicitDisableStackTraceCapture");
            var exceptionWithoutStackTrace = SimpleException.builder().withoutStackTrace().message("OOOPS!").build();
            assertThat(exceptionWithoutStackTrace.getStackTrace()).isEmpty();
            var cause = getCauseThrowable();
            var exceptionWithCauseWithoutStackTrace = SimpleException.builder()
                    .withoutStackTrace()
                    .withCause(cause)
                    .message("OOOPS!")
                    .build();
            assertThat(exceptionWithCauseWithoutStackTrace.getStackTrace()).isEmpty();
            assertThat(exceptionWithCauseWithoutStackTrace)
                    .hasStackTraceContaining("getCauseThrowable")
                    .hasCause(cause);
        } finally {
            disableGlobalStackTraceCapture();
        }
    }

    private static Throwable getCauseThrowable() {
        return new Throwable();
    }

    private static void enableGlobalStackTraceCapture() {
        System.setProperty("smithy.java.captureExceptionStackTraces", "true");
    }

    private static void disableGlobalStackTraceCapture() {
        System.setProperty("smithy.java.captureExceptionStackTraces", "false");
    }
}
