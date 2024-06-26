/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.events.aws;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.serde.event.FrameEncoder;

public final class AwsFrameEncoder implements FrameEncoder<AwsEventFrame> {

    @Override
    public ByteBuffer encode(AwsEventFrame frame) {
        return frame.unwrap().toByteBuffer();
    }
}
