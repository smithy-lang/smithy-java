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

public class ResponseHookTest {
    @Test
    public void usesSameInstanceIfValueUnchanged() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var request = new MyRequest();
        var response = new MyResponse();
        var hook = new ResponseHook<>(TestStructs.OPERATION, context, foo, request, response);

        assertThat(hook.withResponse(response), sameInstance(hook));
        assertThat(hook.withResponse(new MyResponse()), not(sameInstance(hook)));
    }

    @Test
    public void mapsValueIfExpectedType() {
        var foo = new TestStructs.Foo();
        var context = Context.create();
        var request = new MyRequest();
        var response = new MyResponse();
        var hook = new ResponseHook<>(TestStructs.OPERATION, context, foo, request, response);

        assertThat(hook.mapResponse(TestStructs.Bar.class, ResponseHook::response), sameInstance(response));
        assertThat(hook.mapResponse(MyResponse.class, f -> new MyResponse()), not(sameInstance(response)));
    }

    private static final class MyRequest {}

    private static final class MyResponse {}
}
