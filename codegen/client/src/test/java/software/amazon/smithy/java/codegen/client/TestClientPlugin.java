/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.codegen.client.settings.AbSetting;
import software.amazon.smithy.java.codegen.client.settings.Nested;
import software.amazon.smithy.java.codegen.client.settings.OverriddenSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;
import software.amazon.smithy.java.runtime.client.core.annotations.Parameter;

public final class TestClientPlugin implements ClientPlugin, AbSetting, OverriddenSetting, Nested {
    public static final Context.Key<String> CONSTANT_KEY = Context.key("A constant value.");
    public static final Context.Key<BigDecimal> VALUE_KEY = Context.key("A required value.");
    public static final Context.Key<List<String>> STRING_LIST_KEY = Context.key("A list of strings.");
    public static final Context.Key<String> FOO_KEY = Context.key("A combined string value.");
    public static final Context.Key<List<String>> BAZ_KEY = Context.key("A list of strings.");

    @Configuration
    public void value(Context context, @Parameter("data") long value) {
        context.put(VALUE_KEY, BigDecimal.valueOf(value));
    }

    @Configuration
    public void value(Context context, @Parameter("data") double value) {
        context.put(VALUE_KEY, BigDecimal.valueOf(value));
    }

    @Configuration
    public void singleVarargs(Context context, String... strings) {
        context.put(STRING_LIST_KEY, Arrays.asList(strings));
    }

    @Configuration
    public void multiVarargs(Context context, String foo, String... baz) {
        context.put(FOO_KEY, foo);
        context.put(BAZ_KEY, Arrays.asList(baz));
    }

    @Override
    public void overridden(Context context, String value) {
        context.put(OverriddenSetting.OVERRIDE_KEY, value);
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.putConfig(CONSTANT_KEY, "CONSTANT");
        config.context().expect(VALUE_KEY);
    }
}
