/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ContextTest {

    private static final Context.Key<String> FOO = Context.key("Foo");
    private static final Context.Key<Integer> BAR = Context.key("Foo");

    @Test
    public void getTypedValue() {
        var context = Context.create();
        context.put(FOO, "Hi");
        context.put(BAR, 1);

        assertThat(context.get(FOO), equalTo("Hi"));
        assertThat(context.expect(FOO), equalTo("Hi"));

        assertThat(context.get(BAR), is(1));
        assertThat(context.expect(BAR), is(1));
    }

    @Test
    public void returnsNullWhenNotFound() {
        var context = Context.create();

        assertThat(context.get(FOO), nullValue());
    }

    @Test
    public void throwsWhenExpectedAndNotFound() {
        var context = Context.create();

        Assertions.assertThrows(NullPointerException.class, () -> context.expect(FOO));
    }

    @Test
    public void computesAndSets() {
        var context = Context.create();

        assertThat(context.computeIfAbsent(FOO, key -> "hi"), equalTo("hi"));
        assertThat(context.computeIfAbsent(FOO, key -> "bye"), equalTo("hi"));
    }

    @Test
    public void addContext() {
        var context = Context.create();
        context.put(FOO, "hi");

        var overrides = Context.create();
        overrides.put(FOO, "bye");
        overrides.put(BAR, 1);

        context.add(overrides);

        assertThat(context.get(FOO), equalTo("bye"));
        assertThat(context.get(BAR), is(1));
    }
}
