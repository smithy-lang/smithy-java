/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.waiters.backoff.BackoffStrategy;
import software.amazon.smithy.java.waiters.matching.Acceptor;
import software.amazon.smithy.java.waiters.matching.Matcher;
import software.amazon.smithy.java.waiters.models.GetFoosInput;
import software.amazon.smithy.java.waiters.models.GetFoosOutput;

public class TestWaiters {
    private static final String ID = "test-id";
    @Test
    void test() {
        var client = new MockClient(ID,
                List.of(
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("DONE")));
        var waiter = Waiter.builder(client::getFoosSync)
                .addAcceptor(Acceptor.success(
                        Matcher.output(o -> o.status().equals("DONE"))))
                .build();
        // Waiter will throw on failure.
        waiter.wait(new GetFoosInput(ID), 20000);
    }

    @Test
    void testWaiterFailureMatch() {
        var client = new MockClient(ID,
                List.of(
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("DONE")));
        var waiter = Waiter.builder(client::getFoosSync)
                .addAcceptor(Acceptor.failure(
                        Matcher.output(o -> o.status().equals("DONE"))))
                .build();
        var exc = assertThrows(
                WaiterFailureException.class,
                () -> waiter.wait(new GetFoosInput(ID), 20000));
        assertNull(exc.getCause());
        assertEquals(exc.getMessage(), "Waiter reached terminal, FAILURE state");
    }

    @Test
    void testWaiterTimesOut() {
        var client = new MockClient(ID,
                List.of(
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("BUILDING"),
                        new GetFoosOutput("BUILDING")));
        var waiter = Waiter.builder(client::getFoosSync)
                .backoffStrategy(BackoffStrategy.getDefault(10L, 20L))
                .addAcceptor(Acceptor.failure(Matcher.output(o -> o.status().equals("DONE"))))
                .build();
        var exc = assertThrows(
                WaiterFailureException.class,
                () -> waiter.wait(new GetFoosInput(ID), 10));
        assertNull(exc.getCause());
        assertTrue(exc.getMessage().contains("Waiter timed out after"));
    }

    @Test
    void testWaiterWrapsError() {
        var client = new MockClient(ID, List.of(new UnexpectedException("borked")));
        var waiter = Waiter.builder(client::getFoosSync)
                .addAcceptor(Acceptor.failure(Matcher.output(o -> o.status().equals("DONE"))))
                .build();
        var exc = assertThrows(
                WaiterFailureException.class,
                () -> waiter.wait(new GetFoosInput(ID), 10));
        assertEquals(exc.getMessage(), "Waiter encountered unexpected exception.");
        assertInstanceOf(UnexpectedException.class, exc.getCause());
    }

    private static final class UnexpectedException extends ModeledException {

        private UnexpectedException(String message) {
            super(null, message);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            // do nothing
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
