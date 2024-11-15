/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

public class OutputHookTest {
    @Test
    public void usesSameInstanceIfValueUnchanged() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var hook = new OutputHook<>(TestStructs.OPERATION, context, foo, null, null, foo);

        assertThat(hook.withOutput(foo), sameInstance(hook));
        assertThat(hook.withOutput(new TestStructs.Foo()), not(sameInstance(hook)));
    }

    @Test
    public void mapsValueIfExpectedType() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var hook = new OutputHook<>(TestStructs.OPERATION, context, foo, null, null, foo);

        assertThat(hook.mapOutput(TestStructs.Bar.class, OutputHook::output), sameInstance(foo));
        assertThat(hook.mapOutput(TestStructs.Foo.class, f -> new TestStructs.Foo()), not(sameInstance(foo)));
    }
}
