/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.nio.ByteBuffer;

/**
 * Encodes frames to bytes
 */
public interface FrameEncoder<F extends Frame<?>> {
    /**
     * Encode a frame into a buffer
     * @param frame the frame to encode.
     * @return a bytebuffer with the encoded frame's bytes
     */
    ByteBuffer encode(F frame);
}
