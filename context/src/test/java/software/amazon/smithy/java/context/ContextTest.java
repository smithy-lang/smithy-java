/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ContextTest {

    private static final Context.Key<String> FOO = Context.key("Foo");
    private static final Context.Key<Integer> BAR = Context.key("Foo");
    private static final Context.Key<Boolean> BAZ = Context.key("Foo");

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

        assertThrows(NullPointerException.class, () -> context.expect(FOO));
    }

    @Test
    public void computesAndSets() {
        var context = Context.create();

        assertThat(context.computeIfAbsent(FOO, key -> "hi"), equalTo("hi"));
        assertThat(context.computeIfAbsent(FOO, key -> "bye"), equalTo("hi"));
    }

    @Test
    public void unmodifiableView() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        Context unmodifiableView = Context.unmodifiableViewOf(context);

        assertThat(unmodifiableView.get(FOO), equalTo("hi"));
        assertThat(unmodifiableView.expect(FOO), equalTo("hi"));
        assertThat(unmodifiableView.get(BAR), is(1));
        assertThat(unmodifiableView.expect(FOO), equalTo("hi"));

        assertThrows(UnsupportedOperationException.class, () -> unmodifiableView.put(FOO, "bye"));
        assertThrows(UnsupportedOperationException.class, () -> unmodifiableView.putIfAbsent(FOO, "bye"));
        assertThrows(UnsupportedOperationException.class, () -> unmodifiableView.computeIfAbsent(FOO, key -> "bye"));
        assertThrows(UnsupportedOperationException.class, () -> unmodifiableView.putAll(Context.create()));

        Context unmodifiableView2 = Context.unmodifiableViewOf(unmodifiableView);
        assertThat(unmodifiableView2, sameInstance(unmodifiableView));

        context.put(FOO, "bye");
        context.put(BAZ, true);

        assertThat(unmodifiableView.get(FOO), equalTo("bye"));
        assertThat(unmodifiableView.get(BAZ), equalTo(true));
    }

    @Test
    public void putAll() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        var overrides = Context.create();
        context.put(FOO, "bye");
        context.put(BAZ, true);

        context.putAll(overrides);

        assertThat(context.get(FOO), equalTo("bye"));
        assertThat(context.get(BAR), is(1));
        assertThat(context.get(BAZ), equalTo(true));
    }

    @Test
    public void putAllUnmodifiable() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        var overrides = Context.create();
        context.put(FOO, "bye");
        context.put(BAZ, true);

        var unmodifiableOverrides = Context.unmodifiableViewOf(overrides);
        context.putAll(unmodifiableOverrides);

        assertThat(context.get(FOO), equalTo("bye"));
        assertThat(context.get(BAR), is(1));
        assertThat(context.get(BAZ), equalTo(true));
    }
}
