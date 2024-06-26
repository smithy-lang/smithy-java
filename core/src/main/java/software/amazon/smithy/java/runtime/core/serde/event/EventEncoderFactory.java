/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.event;

public interface EventEncoderFactory<F extends Frame<?>> {

    EventEncoder<F> newEventEncoder();

    FrameEncoder<F> newFrameEncoder();

    /**
     * Get the content type for this frame type.
     * @return a content-type.
     */
    String contentType();

}
