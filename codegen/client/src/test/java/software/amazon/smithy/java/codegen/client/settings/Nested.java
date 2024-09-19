/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;

public interface Nested {
    Context.Key<Integer> NESTED_KEY = Context.key("Nested");

    @Configuration
    default void nested(Context context, int nested) {
        context.put(NESTED_KEY, nested);
    }
}
