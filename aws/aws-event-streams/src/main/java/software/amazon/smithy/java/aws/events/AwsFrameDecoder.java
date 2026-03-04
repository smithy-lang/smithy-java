/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import software.amazon.eventstream.MessageDecoder;
import software.amazon.smithy.java.core.serde.event.FrameDecoder;
import software.amazon.smithy.java.core.serde.event.FrameProcessor;

public final class AwsFrameDecoder implements FrameDecoder<AwsEventFrame> {
    private final MessageDecoder decoder = new MessageDecoder();
    private final FrameProcessor<AwsEventFrame> frameProcessor;

    public AwsFrameDecoder(FrameProcessor<AwsEventFrame> frameProcessor) {
        this.frameProcessor = frameProcessor;
    }

    @Override
    public List<AwsEventFrame> decode(ByteBuffer buffer) {
        decoder.feed(buffer);
        var messages = decoder.getDecodedMessages();
        var result = new ArrayList<AwsEventFrame>(messages.size());
        for (var message : messages) {
            var event = new AwsEventFrame(message);
            var transformed = frameProcessor.transformFrame(event);
            if (transformed != null) {
                result.add(transformed);
            }
        }
        return result;
    }
}
