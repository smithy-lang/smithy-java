/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientSetting;

public interface Nested<B extends Client.Builder<?, B>> extends ClientSetting<B> {
    Context.Key<Integer> NESTED_KEY = Context.key("Nested");

    default B nested(int nested) {
        return putConfig(NESTED_KEY, nested);
    }
}
