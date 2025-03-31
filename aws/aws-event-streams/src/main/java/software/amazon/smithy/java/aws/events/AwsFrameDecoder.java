/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import software.amazon.eventstream.MessageDecoder;
import software.amazon.smithy.java.core.serde.event.FrameDecoder;
import software.amazon.smithy.java.core.serde.event.FrameTransformer;

public final class AwsFrameDecoder implements FrameDecoder<AwsEventFrame> {
    private final MessageDecoder decoder = new MessageDecoder();
    private final FrameTransformer<AwsEventFrame> transformer;

    public AwsFrameDecoder(FrameTransformer<AwsEventFrame> transformer) {
        this.transformer = transformer;
    }

    @Override
    public List<AwsEventFrame> decode(ByteBuffer buffer) {
        decoder.feed(buffer);
        return decoder.getDecodedMessages()
                .stream()
                .map(AwsEventFrame::new)
                .map(transformer)
                .filter(Objects::nonNull)
                .toList();
    }
}
