/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.function.IntConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;

final class ResponseStatusSerializer extends SpecificShapeSerializer {

    private final IntConsumer consumer;

    ResponseStatusSerializer(IntConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        consumer.accept(value);
    }
}
