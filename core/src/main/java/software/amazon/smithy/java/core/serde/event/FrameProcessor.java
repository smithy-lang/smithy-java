/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

/**
 * Apply a transformation to a frame, such as signing the frame or validating the signature of a frame
 * and unwrapping the embedded frame within.
 * <p>
 * Null transformation results indicate that the frame should be dropped
 *
 * @param <F> the frame type
 */
@FunctionalInterface
public interface FrameProcessor<F extends Frame<?>> {

    /**
     * Applies a transformation to a frame.
     *
     * @param frame the frame to transform
     * @return the transformed frame.
     */
    F transformFrame(F frame);

    /**
     * Returns a closing frame to be sent when the event stream is closed.
     * This method returns null if no closing frame is needed.
     * <p>
     * A closing empty frame is needed by some auth-schemes such as AWS SigV4.
     *
     * @return the closing frame.
     */
    default F closingFrame() {
        return null;
    }

    /**
     * An identity transformer that returns the same frame as given.
     *
     * @param <F> the frame type
     * @return an identity frame transformer
     */
    static <F extends Frame<?>> FrameProcessor<F> identity() {
        return (F frame) -> frame;
    }
}
