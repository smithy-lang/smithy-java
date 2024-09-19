/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;
import software.amazon.smithy.java.runtime.client.core.annotations.Parameter;

public interface AbSetting {
    Context.Key<String> AB_KEY = Context.key("A combined string value.");

    @Configuration
    default void multiValue(Context context, String valueA, @Parameter("b") String valueB) {
        context.put(AB_KEY, valueA + valueB);
    }
}
