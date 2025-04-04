/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.serde.event.FrameEncoder;

public final class AwsFrameEncoder implements FrameEncoder<AwsEventFrame> {

    @Override
    public ByteBuffer encode(AwsEventFrame frame) {
        return frame.unwrap().toByteBuffer();
    }
}
