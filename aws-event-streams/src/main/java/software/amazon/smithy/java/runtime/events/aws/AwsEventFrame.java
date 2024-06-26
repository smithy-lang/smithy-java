/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.events.aws;

import software.amazon.eventstream.Message;
import software.amazon.smithy.java.runtime.core.serde.event.Frame;

public final class AwsEventFrame implements Frame<Message> {

    private final Message message;

    AwsEventFrame(Message message) {
        this.message = message;
    }

    @Override
    public Message unwrap() {
        return message;
    }
}
