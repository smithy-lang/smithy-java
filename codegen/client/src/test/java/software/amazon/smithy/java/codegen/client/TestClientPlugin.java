/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;

public final class TestClientPlugin implements ClientPlugin {
    public static final Context.Key<String> CONSTANT_KEY = Context.key("A constant value.");

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.putConfig(CONSTANT_KEY, "CONSTANT");
    }
}
