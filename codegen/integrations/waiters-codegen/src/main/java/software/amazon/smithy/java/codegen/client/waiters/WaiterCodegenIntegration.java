/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.waiters;

import java.util.List;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class WaiterCodegenIntegration implements JavaCodegenIntegration {
    @Override
    public String name() {
        return "waiters";
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, JavaWriter>> interceptors(
            CodeGenerationContext context
    ) {
        return List.of(
                new WaiterClientInterfaceMethodInterceptor(context.symbolProvider(), context.settings()),
                new WaiterClientImplMethodInterceptor(context.symbolProvider(), context.settings()),
                new WaiterDocumentationInterceptor());
    }

    @Override
    public void customize(CodeGenerationContext context) {
        new WaiterContainerGenerator().accept(context);
    }
}
