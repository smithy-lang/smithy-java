/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.util.function.Function;

/**
 * Apply a transformation to a frame, such as validating the signature of a frame
 * and unwrapping the embedded frame within.
 * <p>
 * Null transformation results indicate that the frame should be dropped
 *
 * @param <F> the frame type
 */
@FunctionalInterface
public interface FrameTransformer<F extends Frame<?>> extends Function<F, F> {}
