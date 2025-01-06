/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.examples.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.example.events.model.NewOrderEvent;
import software.amazon.smithy.java.example.events.model.QueryEvent;
import software.amazon.smithy.java.example.events.model.ReturnEvent;

public class GenericEventTest {
    private final TypeRegistry typeRegistry = TypeRegistry.builder()
            .putType(NewOrderEvent.$ID, NewOrderEvent.class, NewOrderEvent::builder)
            .putType(QueryEvent.$ID, QueryEvent.class, QueryEvent::builder)
            .putType(ReturnEvent.$ID, ReturnEvent.class, ReturnEvent::builder)
            .build();
    private final Instant now = Instant.now();
    private final List<SerializableShape> inputs = List.of(
            NewOrderEvent.builder().quantity(2).itemId("one").timestamp(now).build(),
            QueryEvent.builder().itemId("one").timestamp(now.plusMillis(1)).build(),
            QueryEvent.builder().itemId("two").timestamp(now.plusMillis(2)).build(),
            ReturnEvent.builder().quantity(1).reason("because").itemId("one").timestamp(now.plusMillis(4)).build(),
            QueryEvent.builder().itemId("two").timestamp(now.plusMillis(6)).build(),
            NewOrderEvent.builder().quantity(1).itemId("two").timestamp(now.plusMillis(7)).build());

    @Test
    void mockEventSystem() {
        Queue<Document> queue = new ArrayBlockingQueue<>(10);
        for (SerializableShape input : inputs) {
            queue.add(Document.of(input));
        }

        List<SerializableShape> result = new ArrayList<>();
        for (var input : queue) {
            result.add(typeRegistry.deserialize(input));
        }

        assertThat(inputs, containsInAnyOrder(result.toArray()));
    }
}
