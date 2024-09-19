/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;

public interface OverriddenSetting {
    Context.Key<String> OVERRIDE_KEY = Context.key("a key for an overridden setting.");

    @Configuration
    default void overridden(Context context, String value) {
        throw new RuntimeException("You shall not pass!");
    }
}
