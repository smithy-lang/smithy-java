/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

public interface AbSetting<B extends Client.Builder<?, B>> extends ClientSetting<B> {
    Context.Key<String> AB_KEY = Context.key("A combined string value.");

    default B multiValue(String valueA, String valueB) {
        return putConfig(AB_KEY, valueA + valueB);
    }
}
