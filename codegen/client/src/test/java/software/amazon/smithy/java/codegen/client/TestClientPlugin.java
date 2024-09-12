/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.ClientConfig;
import software.amazon.smithy.java.runtime.client.ClientPlugin;
import software.amazon.smithy.java.runtime.client.annotations.Configuration;
import software.amazon.smithy.java.runtime.client.annotations.Parameter;

public final class TestClientPlugin implements ClientPlugin {
    public static final Context.Key<String> CONSTANT_KEY = Context.key("A constant value.");
    public static final Context.Key<BigDecimal> VALUE_KEY = Context.key("A required value.");
    public static final Context.Key<String> AB_KEY = Context.key("A combined string value.");
    public static final Context.Key<List<String>> STRING_LIST_KEY = Context.key("A list of strings.");
    public static final Context.Key<String> FOO_KEY = Context.key("A combined string value.");
    public static final Context.Key<List<String>> BAZ_KEY = Context.key("A list of strings.");

    private BigDecimal value;
    private String ab = "";
    private List<String> stringList = new ArrayList<>();
    private String foo = "";
    private List<String> baz = new ArrayList<>();

    @Configuration
    public void value(@Parameter("value") long value) {
        this.value = BigDecimal.valueOf(value);
    }

    @Configuration
    public void value(@Parameter("value") double value) {
        this.value = BigDecimal.valueOf(value);
    }

    @Configuration
    public void multiValue(String valueA, String valueB) {
        this.ab = valueA + valueB;
    }

    @Configuration
    public void singleVarargs(String... strings) {
        this.stringList.addAll(Arrays.asList(strings));
    }

    @Configuration
    public void multiVarargs(String foo, String... baz) {
        this.foo = foo;
        this.baz.addAll(Arrays.asList(baz));
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        Objects.requireNonNull(value, "Value cannot be null!");
        config.putConfig(VALUE_KEY, value);
        config.putConfig(AB_KEY, ab);
        config.putConfig(STRING_LIST_KEY, stringList);
        config.putConfig(FOO_KEY, foo);
        config.putConfig(BAZ_KEY, baz);
        config.putConfig(CONSTANT_KEY, "CONSTANT");
    }
}
