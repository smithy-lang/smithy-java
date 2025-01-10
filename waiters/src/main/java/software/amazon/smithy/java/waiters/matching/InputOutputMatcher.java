/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.matching;

import java.util.function.BiPredicate;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;

record InputOutputMatcher<I extends SerializableStruct, O extends SerializableStruct>(BiPredicate<I, O> predicate)
        implements Matcher<I, O> {
    @Override
    public boolean matches(I input, O output, ModeledException exception) {
        if (input == null || output == null || exception != null) {
            return false;
        }
        return predicate.test(input, output);
    }
}
