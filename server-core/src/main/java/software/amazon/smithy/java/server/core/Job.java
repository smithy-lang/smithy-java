/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.runtime.core.Context;

public sealed interface Job permits JobImpl {

    Request getRequest();

    Reply getReply();

    Context getContext();
}
