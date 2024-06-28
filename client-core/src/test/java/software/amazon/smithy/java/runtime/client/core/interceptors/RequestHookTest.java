/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.Context;

public class RequestHookTest {
    @Test
    public void usesSameInstanceIfValueUnchanged() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var request = new MyRequest();
        var hook = new RequestHook<>(context, foo, request);

        assertThat(hook.withRequest(request), sameInstance(hook));
        assertThat(hook.withRequest(new MyRequest()), not(sameInstance(hook)));
    }

    @Test
    public void mapsValueIfExpectedType() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var request = new MyRequest();
        var hook = new RequestHook<>(context, foo, request);

        assertThat(hook.mapRequest(TestStructs.Bar.class, bar -> bar), sameInstance(request));
        assertThat(hook.mapRequest(MyRequest.class, f -> new MyRequest()), not(sameInstance(request)));
    }

    private static final class MyRequest {}
}
