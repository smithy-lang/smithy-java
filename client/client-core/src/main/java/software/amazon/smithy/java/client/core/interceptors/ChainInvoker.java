/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import software.amazon.smithy.java.core.schema.SerializableStruct;

final class ChainInvoker<I extends SerializableStruct, O extends SerializableStruct>
        implements ClientInterceptor.NextCall<I, O> {

    private final ClientInterceptor[] wrappers;
    private final ClientInterceptor.NextCall<I, O> terminal;
    private int index;

    ChainInvoker(ClientInterceptor[] wrappers, ClientInterceptor.NextCall<I, O> terminal) {
        this.wrappers = wrappers;
        this.terminal = terminal;
    }

    @Override
    public O invoke(InputHook<I, O> hook) {
        int i = index++;
        try {
            return i == wrappers.length
                    ? terminal.invoke(hook)
                    : wrappers[i].interceptCall(hook, this);
        } finally {
            index = i;
        }
    }
}
