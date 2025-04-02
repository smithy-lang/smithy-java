/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

public interface TestSettings<B extends ClientSetting<B>> extends AbSetting<B>, NestedSettings<B> {
    Context.Key<BigDecimal> VALUE_KEY = Context.key("A required value.");
    Context.Key<List<String>> STRING_LIST_KEY = Context.key("A list of strings.");
    Context.Key<String> FOO_KEY = Context.key("A combined string value.");
    Context.Key<List<String>> BAZ_KEY = Context.key("A list of strings.");

    default B value(long value) {
        return putConfig(VALUE_KEY, BigDecimal.valueOf(value));
    }

    default B value(double value) {
        return putConfig(VALUE_KEY, BigDecimal.valueOf(value));
    }

    default B singleVarargs(String... strings) {
        return putConfig(STRING_LIST_KEY, Arrays.asList(strings));
    }

    default B multiVarargs(String foo, String... baz) {
        putConfig(FOO_KEY, foo);
        return putConfig(BAZ_KEY, Arrays.asList(baz));
    }
}
