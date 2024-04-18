/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.function.IntConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;

final class ResponseStatusSerializer extends SpecificShapeSerializer {

    private final IntConsumer consumer;

    ResponseStatusSerializer(IntConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        consumer.accept(value);
    }
}
