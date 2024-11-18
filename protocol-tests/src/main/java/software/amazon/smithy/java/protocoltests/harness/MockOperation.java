/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class MockOperation implements InvocationHandler {

    private final Object target;
    private final AtomicReference<Object> request = new AtomicReference<>();
    private final AtomicReference<Object> response = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean wasInvoked = new AtomicBoolean(false);

    public MockOperation(Class<?> operationClazz) {
        this.target = Proxy.newProxyInstance(MockOperation.class.getClassLoader(), new Class[]{operationClazz}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        wasInvoked.set(true);
        request.set(args[0]);
        Throwable exception = this.error.get();
        if (exception != null) {
            throw exception;
        }
        return response.get();
    }

    public Object getMock() {
        return target;
    }

    public void setResponse(Object response) {
        this.response.set(response);
    }

    public void setError(Throwable error) {
        this.error.set(error);
    }

    public <T> T getRequest() {
        return (T) request.get();
    }

    public void reset() {
        wasInvoked.set(false);
        request.set(null);
        response.set(null);
        error.set(null);
    }

}
