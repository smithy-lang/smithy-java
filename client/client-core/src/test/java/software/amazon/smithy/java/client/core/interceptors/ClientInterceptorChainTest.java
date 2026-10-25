/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.amazon.smithy.java.client.core.interceptors.ClientInterceptorChain.overrides;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptorChain.Hook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.SerializableStruct;

public class ClientInterceptorChainTest {

    private static final class CountingInterceptor implements ClientInterceptor {
        final AtomicInteger readBeforeExecution = new AtomicInteger();
        final AtomicInteger modifyBeforeSigning = new AtomicInteger();

        @Override
        public void readBeforeExecution(InputHook<?, ?> hook) {
            readBeforeExecution.incrementAndGet();
        }

        @Override
        public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
            modifyBeforeSigning.incrementAndGet();
            return hook.request();
        }
    }

    private abstract static class Base implements ClientInterceptor {
        final AtomicInteger readAfterExecution = new AtomicInteger();

        @Override
        public void readAfterExecution(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
            readAfterExecution.incrementAndGet();
        }
    }

    private static final class Derived extends Base {}

    private interface ReadsTransmit extends ClientInterceptor {
        @Override
        default void readAfterTransmit(ResponseHook<?, ?, ?, ?> hook) {
            onReadAfterTransmit();
        }

        void onReadAfterTransmit();
    }

    private static final class ViaInterface implements ReadsTransmit {
        final AtomicInteger readAfterTransmit = new AtomicInteger();

        @Override
        public void onReadAfterTransmit() {
            readAfterTransmit.incrementAndGet();
        }
    }

    private static final class HandlingInterceptor implements ClientInterceptor {
        final AtomicInteger handled = new AtomicInteger();

        @Override
        public <O extends SerializableStruct> O modifyBeforeAttemptCompletion(
                OutputHook<?, O, ?, ?> hook,
                RuntimeException error
        ) {
            handled.incrementAndGet();
            return hook.output();
        }

        @Override
        public <O extends SerializableStruct> O modifyBeforeCompletion(
                OutputHook<?, O, ?, ?> hook,
                RuntimeException error
        ) {
            handled.incrementAndGet();
            return hook.output();
        }
    }

    @Test
    public void detectsOverridesForEachKindOfImplementation() {
        var anonymous = new ClientInterceptor() {
            @Override
            public void readBeforeAttempt(RequestHook<?, ?, ?> hook) {}
        };

        assertThat(overrides(CountingInterceptor.class, Hook.READ_BEFORE_EXECUTION), is(true));
        assertThat(overrides(CountingInterceptor.class, Hook.MODIFY_BEFORE_SIGNING), is(true));
        assertThat(overrides(CountingInterceptor.class, Hook.READ_BEFORE_SERIALIZATION), is(false));
        assertThat(overrides(Derived.class, Hook.READ_AFTER_EXECUTION), is(true));
        assertThat(overrides(ViaInterface.class, Hook.READ_AFTER_TRANSMIT), is(true));
        assertThat(overrides(anonymous.getClass(), Hook.READ_BEFORE_ATTEMPT), is(true));
        assertThat(overrides(anonymous.getClass(), Hook.READ_AFTER_ATTEMPT), is(false));
        for (var hook : Hook.values()) {
            assertThat(hook.name(), overrides(ClientInterceptor.NOOP.getClass(), hook), is(false));
            assertThat(hook.name(), overrides(ClientInterceptorChain.class, hook), is(true));
        }
    }

    @Test
    public void hooksCoverEveryDispatchedInterceptorMethod() {
        Set<Method> dispatched = Arrays.stream(ClientInterceptor.class.getMethods())
                .filter(Method::isDefault)
                .filter(m -> !m.getName().equals("interceptCalls") && !m.getName().equals("interceptCall"))
                .collect(Collectors.toSet());
        Set<Method> hooks = Arrays.stream(Hook.values()).map(h -> h.method).collect(Collectors.toSet());

        assertThat(hooks, equalTo(dispatched));
    }

    @Test
    public void dispatchesOnlyToOverridingInterceptors() {
        var counting = new CountingInterceptor();
        var derived = new Derived();
        var viaInterface = new ViaInterface();
        var chain = ClientInterceptor.chain(List.of(counting, derived, viaInterface, ClientInterceptor.NOOP));

        var input = new TestStructs.Foo();
        var inputHook = new InputHook<>(TestStructs.OPERATION, Context.create(), input);
        chain.readBeforeExecution(inputHook);
        chain.readBeforeSerialization(inputHook);
        assertThat(counting.readBeforeExecution.get(), equalTo(1));

        var request = new Object();
        var requestHook = new RequestHook<>(TestStructs.OPERATION, Context.create(), input, request);
        assertThat(chain.modifyBeforeSigning(requestHook), sameInstance(request));
        assertThat(chain.modifyBeforeTransmit(requestHook), sameInstance(request));
        assertThat(chain.modifyBeforeRetryLoop(requestHook), sameInstance(request));
        assertThat(counting.modifyBeforeSigning.get(), equalTo(1));

        var responseHook = new ResponseHook<>(TestStructs.OPERATION, Context.create(), input, request, new Object());
        chain.readAfterTransmit(responseHook);
        assertThat(viaInterface.readAfterTransmit.get(), equalTo(1));

        var outputHook = new OutputHook<>(TestStructs.OPERATION, Context.create(), input, request, new Object(), null);
        chain.readAfterExecution(outputHook, null);
        assertThat(derived.readAfterExecution.get(), equalTo(1));
    }

    @Test
    public void readAfterExecutionRethrowsWithoutOverriders() {
        var chain = ClientInterceptor.chain(List.of(new CountingInterceptor(), ClientInterceptor.NOOP));
        var input = new TestStructs.Foo();
        var outputHook =
                new OutputHook<>(TestStructs.OPERATION, Context.create(), input, new Object(), new Object(), null);
        var error = new IllegalStateException("boom");

        var thrown = assertThrows(IllegalStateException.class, () -> chain.readAfterExecution(outputHook, error));
        assertThat(thrown, sameInstance(error));
        assertDoesNotThrow(() -> chain.readAfterAttempt(outputHook, error));
    }

    @Test
    public void readAfterAttemptThrowsReplacementErrors() {
        var replacement = new IllegalArgumentException("replaced");
        var replacing = new ClientInterceptor() {
            @Override
            public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
                throw replacement;
            }
        };
        var chain = ClientInterceptor.chain(List.of(ClientInterceptor.NOOP, replacing));
        var input = new TestStructs.Foo();
        var outputHook =
                new OutputHook<>(TestStructs.OPERATION, Context.create(), input, new Object(), new Object(), null);

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> chain.readAfterAttempt(outputHook, new IllegalStateException("original")));
        assertThat(thrown, is(sameInstance(replacement)));
    }

    @Test
    public void modifyBeforeAttemptCompletionForwardsPendingErrorsLikeTheDefault() {
        assertCompletionHookForwardsLikeTheDefault(ClientInterceptor::modifyBeforeAttemptCompletion);
    }

    @Test
    public void modifyBeforeCompletionForwardsPendingErrorsLikeTheDefault() {
        assertCompletionHookForwardsLikeTheDefault(ClientInterceptor::modifyBeforeCompletion);
    }

    @FunctionalInterface
    private interface CompletionHook {
        SerializableStruct call(
                ClientInterceptor chain,
                OutputHook<?, TestStructs.Foo, ?, ?> hook,
                RuntimeException error
        );
    }

    private static void assertCompletionHookForwardsLikeTheDefault(CompletionHook call) {
        var handling = new HandlingInterceptor();
        var input = new TestStructs.Foo();
        var outputHook =
                new OutputHook<>(TestStructs.OPERATION, Context.create(), input, new Object(), new Object(), input);
        var error = new IllegalStateException("boom");

        var allOverride = ClientInterceptor.chain(List.of(handling, handling));
        assertThat(call.call(allOverride, outputHook, error), sameInstance(input));
        assertThat(handling.handled.get(), equalTo(2));

        handling.handled.set(0);
        var mixed = ClientInterceptor.chain(List.of(handling, ClientInterceptor.NOOP, handling));
        var thrown = assertThrows(IllegalStateException.class, () -> call.call(mixed, outputHook, error));
        assertThat(thrown, sameInstance(error));
        assertThat(handling.handled.get(), equalTo(1));

        handling.handled.set(0);
        assertThat(call.call(mixed, outputHook, null), sameInstance(input));
        assertThat(handling.handled.get(), equalTo(2));

        var noOverriders = ClientInterceptor.chain(List.of(ClientInterceptor.NOOP, new CountingInterceptor()));
        thrown = assertThrows(IllegalStateException.class, () -> call.call(noOverriders, outputHook, error));
        assertThat(thrown, sameInstance(error));
        assertThat(call.call(noOverriders, outputHook, null), sameInstance(input));
    }
}
