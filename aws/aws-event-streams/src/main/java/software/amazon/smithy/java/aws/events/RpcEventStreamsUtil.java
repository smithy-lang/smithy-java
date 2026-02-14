/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.core.serde.event.InternalEventStreamReader;
import software.amazon.smithy.java.core.serde.event.InternalEventStreamWriter;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * A util class to serialize and deserialize event streams responses for RPC protocols.
 */
public final class RpcEventStreamsUtil {

    private RpcEventStreamsUtil() {}

    @SuppressWarnings("unchecked")
    public static DataStream bodyForEventStreaming(
            EventEncoderFactory<AwsEventFrame> eventStreamEncodingFactory,
            SerializableStruct input
    ) {
        EventStream<SerializableStruct> eventStream = input.getMemberValue(streamingMember(input.schema()));
        InternalEventStreamWriter<SerializableStruct, SerializableStruct, AwsEventFrame> writer =
                InternalEventStreamWriter.toInternal(eventStream);
        writer.bootstrap(new InternalEventStreamWriter.Bootstrap<>() {
            @Override
            public EventEncoderFactory<AwsEventFrame> encoder() {
                return eventStreamEncodingFactory;
            }

            @Override
            public SerializableStruct initialEvent() {
                return input;
            }
        });
        return writer.toDataStream();
    }

    public static <O extends SerializableStruct> O deserializeResponse(
            EventDecoderFactory<AwsEventFrame> eventDecoderFactory,
            DataStream bodyDataStream
    ) {
        var reader = InternalEventStreamReader.<O, SerializableStruct, AwsEventFrame>newReader(bodyDataStream,
                eventDecoderFactory,
                true);
        return reader.readInitialEvent();
    }

    private static Schema streamingMember(Schema schema) {
        for (var member : schema.members()) {
            if (member.isMember() && member.memberTarget().hasTrait(TraitKey.STREAMING_TRAIT)) {
                return member;
            }
        }
        throw new IllegalArgumentException("No streaming member found");
    }
}
