/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamFrameEncodingProcessor;

/**
 * A util class to serialize event streams requests for RPC protocols.
 */
public final class RpcEventStreamsRequest {

    private RpcEventStreamsRequest() {}

    public static Flow.Publisher<ByteBuffer> bodyForEventStreaming(
            EventEncoderFactory<AwsEventFrame> eventStreamEncodingFactory,
            SerializableStruct input
    ) {
        Flow.Publisher<SerializableStruct> eventStream = input.getMemberValue(streamingMember(input.schema()));
        var publisher = EventStreamFrameEncodingProcessor.create(eventStream, eventStreamEncodingFactory);
        // Queue the input as the initial-request.
        publisher.onNext(input);
        return publisher;
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
