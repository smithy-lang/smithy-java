/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events.model;

import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Service API schema
 */
@SmithyGenerated
public final class EventStreamingTestServiceApiService implements ApiService {
    private static final EventStreamingTestServiceApiService $INSTANCE = new EventStreamingTestServiceApiService();
    private static final Schema $SCHEMA =
            Schema.createService(ShapeId.from("smithy.test.eventstreaming#EventStreamingTestService"));

    /**
     * Get an instance of this {@code ApiService}.
     *
     * @return An instance of this class.
     */
    public static EventStreamingTestServiceApiService instance() {
        return $INSTANCE;
    }

    private EventStreamingTestServiceApiService() {}

    @Override
    public Schema schema() {
        return $SCHEMA;
    }
}
