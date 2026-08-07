/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.server;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.io.ByteBufferUtils;

/**
 * Publishes generated event types to an SNS topic.
 *
 * <p>Events are ordinary generated structures, so one method serializes any of them.
 */
public final class EventPublisher {

    // Matches the protocol the service speaks.
    private static final Codec CODEC = Rpcv2CborCodec.builder().build();

    private final SnsClient sns;
    private final String topicArn;

    public EventPublisher(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    /** Serializes an event to CBOR and publishes it. */
    public void publish(SerializableStruct event) {
        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                // An SNS message body must be a UTF-8 string, so CBOR needs encoding.
                .message(ByteBufferUtils.base64Encode(CODEC.serialize(event)))
                // Lets subscribers filter without decoding the body.
                .subject(event.schema().id().getName())
                .build());
    }
}
