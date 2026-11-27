/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * A modeled error event whose shape does not build an exception, such as a document from the dynamic client.
 */
final class EventStreamModeledException extends ModeledException {

    private final SerializableShape error;

    EventStreamModeledException(Schema schema, SerializableShape error) {
        super(schema, createMessage(schema, error));
        this.error = error;
    }

    private static String createMessage(Schema schema, SerializableShape error) {
        var message = schema.id().getName() + " error";
        if (error instanceof SerializableStruct struct) {
            for (var name : new String[] {"message", "Message"}) {
                var member = schema.member(name);
                if (member != null && struct.getMemberValue(member) instanceof String value) {
                    return message + ": " + value;
                }
            }
        }
        return message;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        error.serialize(encoder);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (error instanceof SerializableStruct struct) {
            struct.serializeMembers(serializer);
        }
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        if (error instanceof SerializableStruct struct) {
            return struct.getMemberValue(member);
        }
        return null;
    }
}
