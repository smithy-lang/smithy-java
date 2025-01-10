/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.matching;

import java.util.function.Predicate;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;

record OutputMatcher<I extends SerializableStruct, O extends SerializableStruct>(Predicate<O> predicate)
        implements Matcher<I, O> {
    @Override
    public boolean matches(I input, O output, ModeledException exception) {
        if (output == null || exception != null) {
            return false;
        }
        return predicate.test(output);
    }
}
