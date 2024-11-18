/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.platform.commons.util.ReflectionUtils;

public class ReloadClassesExtension implements InvocationInterceptor {

    @Override
    @SuppressFBWarnings("DP_CREATE_CLASSLOADER_INSIDE_DO_PRIVILEGED")
    public void interceptTestMethod(
        Invocation<Void> invocation,
        ReflectiveInvocationContext<Method> invocationContext,
        ExtensionContext extensionContext
    ) throws Throwable {
        ReloadClasses annotation = invocationContext.getExecutable().getAnnotation(ReloadClasses.class);
        if (annotation == null) {
            InvocationInterceptor.super.interceptTestMethod(invocation, invocationContext, extensionContext);
            return;
        }
        if (invocationContext.getExecutable().isAnnotationPresent(ParameterizedTest.class)) {
            throw new IllegalStateException("ReloadClasses does not support Parameterized tests, yet");
        }
        invocation.skip();
        URL[] jars;
        if (ClassLoader.getSystemClassLoader() instanceof URLClassLoader u) {
            jars = u.getURLs();
        } else {
            String classPath = System.getProperty("java.class.path");
            jars = Arrays.stream(classPath.split(File.pathSeparator))
                .map(this::getURL)
                .toArray(URL[]::new);
        }

        try (URLClassLoader classLoader = new URLClassLoader(jars, null)) {
            invokeMethodWithModifiedClasspath(invocationContext, classLoader);
        }
    }

    private URL getURL(String s) {
        try {
            return new File(s).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeMethodWithModifiedClasspath(
        ReflectiveInvocationContext<Method> invocationContext,
        ClassLoader modifiedClassLoader
    ) {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(modifiedClassLoader);

        try {
            invokeMethodWithModifiedClasspath(
                invocationContext.getExecutable().getDeclaringClass().getName(),
                invocationContext.getExecutable().getName(),
                modifiedClassLoader
            );
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private void invokeMethodWithModifiedClasspath(String className, String methodName, ClassLoader classLoader) {
        final Class<?> testClass;
        try {
            testClass = classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load test class [" + className + "] from modified classloader", e);
        }

        Object testInstance = ReflectionUtils.newInstance(testClass);
        final Optional<Method> method = ReflectionUtils.findMethod(testClass, methodName);
        ReflectionUtils.invokeMethod(
            method.orElseThrow(() -> new IllegalStateException("No test method named " + methodName)),
            testInstance
        );
    }
}
