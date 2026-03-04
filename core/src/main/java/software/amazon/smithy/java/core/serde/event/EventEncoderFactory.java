/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

/**
 * Factory to create encoders to encode the events into frames and frames into bytes.
 *
 * @param <F> The type of the frame.
 */
public interface EventEncoderFactory<F extends Frame<?>> {

    /**
     * Returns a new event encoder.
     *
     * @return a new event encoder
     */
    EventEncoder<F> newEventEncoder();

    /**
     * Returns a new frame encoder.
     *
     * @return a new frame encoder
     */
    FrameEncoder<F> newFrameEncoder();

    /**
     * Get the content type for this frame type.
     *
     * @return a content-type.
     */
    String contentType();

    /**
     * Returns a new factory configured with the given frameProcessor.
     *
     * @param frameProcessor the frameProcessor for the factory
     * @return a new factory configured with the given frameProcessor.
     */
    EventEncoderFactory<F> withFrameProcessor(FrameProcessor<F> frameProcessor);
}
