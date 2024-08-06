/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import java.math.BigDecimal;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;
import software.amazon.smithy.java.runtime.client.core.annotations.Parameter;

public final class TestClientPlugin implements ClientPlugin {
    public static final Context.Key<String> CONSTANT_KEY = Context.key("A constant value.");
    public static final Context.Key<BigDecimal> VALUE_KEY = Context.key("A required value.");

    private BigDecimal value;

    @Configuration
    public void value(@Parameter("value") long value) {
        this.value = BigDecimal.valueOf(value);
    }

    @Configuration
    public void value(@Parameter("value") double value) {
        this.value = BigDecimal.valueOf(value);
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        Objects.requireNonNull(value, "Value cannot be null!");
        config.putConfig(VALUE_KEY, value);
        config.putConfig(CONSTANT_KEY, "CONSTANT");
    }
}
