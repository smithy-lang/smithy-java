/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

public interface EventDecoderFactory<F extends Frame<?>> {

    EventDecoder<F> newEventDecoder();

    FrameDecoder<F> newFrameDecoder();
}
