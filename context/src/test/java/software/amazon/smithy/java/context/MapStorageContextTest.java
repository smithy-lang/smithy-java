/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class MapStorageContextTest {

    @Test
    public void getTypedValue() {
        var context = new MapStorageContext();
        context.put(ContextTest.FOO, "Hi");
        context.put(ContextTest.BAR, 1);

        assertThat(context.get(ContextTest.FOO), equalTo("Hi"));
        assertThat(context.expect(ContextTest.FOO), equalTo("Hi"));

        assertThat(context.get(ContextTest.BAR), is(1));
        assertThat(context.expect(ContextTest.BAR), is(1));
    }

    @Test
    public void returnsNullWhenNotFound() {
        var context = new MapStorageContext();

        assertThat(context.get(ContextTest.FOO), nullValue());
    }

    @Test
    public void throwsWhenExpectedAndNotFound() {
        var context = new MapStorageContext();

        assertThrows(NullPointerException.class, () -> context.expect(ContextTest.FOO));
    }

    @Test
    public void computesAndSets() {
        var context = new MapStorageContext();

        assertThat(context.computeIfAbsent(ContextTest.FOO, key -> "hi"), equalTo("hi"));
        assertThat(context.computeIfAbsent(ContextTest.FOO, key -> "bye"), equalTo("hi"));
    }

    @Test
    public void copyTo() {
        var context = new MapStorageContext();
        context.put(ContextTest.FOO, "hi");
        context.put(ContextTest.BAR, 1);

        var target = Context.create();
        target.put(ContextTest.FOO, "bye");
        target.put(ContextTest.BAZ, true);

        context.copyTo(target);

        assertThat(target.get(ContextTest.FOO), equalTo("hi"));
        assertThat(target.get(ContextTest.BAR), is(1));
        assertThat(target.get(ContextTest.BAZ), equalTo(true));
    }
}
