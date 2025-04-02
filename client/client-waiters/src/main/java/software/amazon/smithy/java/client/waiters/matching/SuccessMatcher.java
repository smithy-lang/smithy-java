/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.matching;

import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;

record SuccessMatcher<I extends SerializableStruct, O extends SerializableStruct>(boolean onSuccess)
        implements Matcher<I, O> {
    @Override
    public boolean matches(SerializableStruct input, SerializableStruct output, ModeledException exception) {
        if (exception != null) {
            return !onSuccess;
        }
        return onSuccess;
    }
}
