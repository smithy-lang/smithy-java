/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.matching;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.waiters.WaiterState;

record AcceptorImpl<I extends SerializableStruct, O extends SerializableStruct>(
        WaiterState state,
        Matcher<I, O> matcher) implements Acceptor<I, O> {}
