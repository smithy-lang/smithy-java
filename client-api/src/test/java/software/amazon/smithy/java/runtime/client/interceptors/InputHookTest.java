/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.interceptors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

public class InputHookTest {
    @Test
    public void usesSameInstanceIfValueUnchanged() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var hook = new InputHook<>(context, foo);

        assertThat(hook.withInput(foo), sameInstance(hook));
        assertThat(hook.withInput(new TestStructs.Foo()), not(sameInstance(hook)));
    }

    @Test
    public void mapsValueIfExpectedType() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var hook = new InputHook<>(context, foo);

        assertThat(hook.mapInput(TestStructs.Bar.class, bar -> bar), sameInstance(foo));
        assertThat(hook.mapInput(TestStructs.Foo.class, f -> new TestStructs.Foo()), not(sameInstance(foo)));
    }
}
