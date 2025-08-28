/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamFrameDecodingProcessor;
import software.amazon.smithy.java.core.serde.event.EventStreamFrameEncodingProcessor;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * A util class to serialize and deserialize event streams responses for RPC protocols.
 */
public final class RpcEventStreamsUtil {

    private RpcEventStreamsUtil() {}

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

    public static <O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
            EventDecoderFactory<AwsEventFrame> eventDecoderFactory,
            DataStream bodyDataStream
    ) {
        var result = new CompletableFuture<O>();
        var processor = EventStreamFrameDecodingProcessor.create(bodyDataStream, eventDecoderFactory);

        // A subscriber to serialize the initial event.
        processor.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onNext(SerializableStruct item) {
                result.complete((O) item);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.completeExceptionally(new RuntimeException("Unexpected vent stream completion"));
            }
        });

        return result;
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
