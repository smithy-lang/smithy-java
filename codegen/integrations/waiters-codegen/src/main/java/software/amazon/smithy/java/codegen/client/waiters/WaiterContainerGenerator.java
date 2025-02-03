/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.waiters;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.client.ClientSymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.waiters.Waiter;
import software.amazon.smithy.java.waiters.backoff.BackoffStrategy;
import software.amazon.smithy.java.waiters.jmespath.Comparator;
import software.amazon.smithy.java.waiters.jmespath.JMESPathBiPredicate;
import software.amazon.smithy.java.waiters.jmespath.JMESPathPredicate;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.StringUtils;
import software.amazon.smithy.waiters.Matcher;
import software.amazon.smithy.waiters.WaitableTrait;

/**
 * Generates the waiter container class for a client
 */
final class WaiterContainerGenerator implements Consumer<CodeGenerationContext> {

    @Override
    public void accept(CodeGenerationContext context) {
        var serviceShape = context.model().expectShape(context.settings().service(), ServiceShape.class);
        var serviceSymbol = context.symbolProvider().toSymbol(serviceShape);
        // Only run generator if service is client
        if (serviceSymbol.getProperty(ClientSymbolProperties.CLIENT_IMPL).isEmpty()) {
            return;
        }
        // TODO : Allow for async waiters.
        var symbol = WaiterCodegenUtils.getWaiterSymbol(serviceSymbol, context.settings(), false);
        context.settings().addSymbol(symbol);
        context.writerDelegator().useSymbolWriter(symbol, writer -> {
            var template = """
                    /**
                     * Waiters for the {@link ${clientType:T}} client.
                     */
                    public record ${type:T}(${clientType:T} client) {
                        public ${type:T} {
                            ${objects:T}.requireNonNull(client, "client cannot be null");
                        }

                        ${waiters:C|}
                    }
                    """;
            writer.putContext("type", symbol);
            writer.putContext("objects", Objects.class);
            var clientSymbol = context.symbolProvider().toSymbol(serviceShape);
            writer.putContext("clientType", clientSymbol);
            var waitableOperations = context.model().getShapesWithTrait(WaitableTrait.class);
            writer.putContext("waiters",
                    new WaiterGenerator(writer,
                            context.symbolProvider(),
                            context.model(),
                            waitableOperations,
                            serviceShape));
            writer.write(template);
        });
    }

    private record WaiterGenerator(
            JavaWriter writer,
            SymbolProvider symbolProvider,
            Model model,
            Set<Shape> waitableOperations,
            ServiceShape service) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            OperationIndex index = OperationIndex.of(model);

            writer.putContext("waiterClass", Waiter.class);

            for (var operation : waitableOperations) {
                var trait = operation.expectTrait(WaitableTrait.class);

                writer.pushState();
                writer.putContext("input", symbolProvider.toSymbol(index.expectInputShape(operation)));
                writer.putContext("output", symbolProvider.toSymbol(index.expectOutputShape(operation)));
                writer.putContext("opName", StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service)));
                writer.putContext("backoff", BackoffStrategy.class);
                writer.putContext("deprecated", Deprecated.class);
                for (var waiterEntry : trait.getWaiters().entrySet()) {
                    writer.pushState(new WaiterSection(waiterEntry.getValue()));
                    writer.putContext("waiterName", waiterEntry.getKey());
                    var waiter = waiterEntry.getValue();
                    // Min and max delay on trait are always in seconds. Convert to millis and add to context
                    writer.putContext("maxDelay", waiter.getMaxDelay() * 1000);
                    writer.putContext("minDelay", waiter.getMinDelay() * 1000);
                    writer.putContext("isDeprecated", waiter.isDeprecated());

                    var template =
                            """
                                    ${?isDeprecated}@${deprecated:T}
                                    ${/isDeprecated}public ${waiterClass:T}<${input:T}, ${output:T}> ${waiterName:L}() {
                                        return ${waiterClass:T}.<${input:T}, ${output:T}>builder(client::${opName:L})
                                            .backoffStrategy(${backoff:T}.getDefault(${maxDelay:L}L, ${minDelay:L}L))${#acceptors}
                                            .${key:L}(${value:C|})${/acceptors}
                                            .build();
                                    }
                                    """;
                    Map<String, Runnable> acceptors = new HashMap<>();
                    for (var acceptor : waiter.getAcceptors()) {
                        acceptors.put(
                                acceptor.getState().name().toLowerCase(Locale.ENGLISH),
                                new MatcherVisitor(writer, acceptor.getMatcher()));
                    }
                    writer.putContext("acceptors", acceptors);
                    writer.write(template);
                    writer.popState();
                }
                writer.popState();
            }
            writer.popState();
        }
    }

    private record MatcherVisitor(JavaWriter writer, Matcher<?> matcher) implements Matcher.Visitor<Void>, Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("matcherType", software.amazon.smithy.java.waiters.matching.Matcher.class);
            matcher.accept(this);
            writer.popState();
        }

        @Override
        public Void visitOutput(Matcher.OutputMember outputMember) {
            writer.pushState();
            writer.putContext("path", outputMember.getValue().getPath());
            writer.putContext("expected", outputMember.getValue().getExpected());
            writer.putContext("comparator", Comparator.class);
            writer.putContext("compType", outputMember.getValue().getComparator().name());
            writer.putContext("pred", JMESPathPredicate.class);
            writer.write(
                    "${matcherType:T}.output(new ${pred:T}(${path:S}, ${expected:S}, ${comparator:T}.${compType:L}))");
            writer.popState();
            return null;
        }

        @Override
        public Void visitInputOutput(Matcher.InputOutputMember inputOutputMember) {
            writer.pushState();
            writer.putContext("path", inputOutputMember.getValue().getPath());
            writer.putContext("expected", inputOutputMember.getValue().getExpected());
            writer.putContext("comparator", Comparator.class);
            writer.putContext("compType", inputOutputMember.getValue().getComparator().name());

            writer.putContext("pred", JMESPathBiPredicate.class);
            writer.write(
                    "${matcherType:T}.inputOutput(new ${pred:T}(${path:S}, ${expected:S}, ${comparator:T}.${compType:L}))");
            writer.popState();
            return null;
        }

        @Override
        public Void visitSuccess(Matcher.SuccessMember successMember) {
            writer.write("${matcherType:T}.success($L)", successMember.getValue());
            return null;
        }

        @Override
        public Void visitErrorType(Matcher.ErrorTypeMember errorTypeMember) {
            writer.write("${matcherType:T}.errorType($S)", errorTypeMember.getValue());
            return null;
        }

        @Override
        public Void visitUnknown(Matcher.UnknownMember unknownMember) {
            throw new IllegalArgumentException("Unknown member " + unknownMember);
        }
    }
}
