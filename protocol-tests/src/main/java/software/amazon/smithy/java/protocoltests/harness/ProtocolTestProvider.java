/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * Common class for Protocol Test {@code TestTemplateInvocationContextProvider}s.
 *
 * @param <T> Annotation used to trigger and configure the test template provider.
 */
abstract class ProtocolTestProvider<T extends Annotation> implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getRequiredTestMethod().isAnnotationPresent(getAnnotationType());
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        // Get all test filters for the given test provider
        var filter = TestFilter.fromAnnotation(context.getRequiredTestMethod().getAnnotation(ProtocolTestFilter.class));

        // Retrieve shared data from extension store
        var outerContext = context.getParent().orElseThrow();
        var store = ProtocolTestExtension.getSharedTestData(outerContext);
        if (store == null) {
            throw new IllegalStateException(
                "Cannot execute protocol tests as no shared data store was found. Add "
                    + "`ProtocolTest` annotation to test class to initialize data store."
            );
        }

        return generateProtocolTests(store, filter);
    }

    protected abstract Class<T> getAnnotationType();

    protected abstract Stream<TestTemplateInvocationContext> generateProtocolTests(
        ProtocolTestExtension.SharedTestData testData,
        TestFilter filter
    );
}
