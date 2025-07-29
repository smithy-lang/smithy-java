/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.jmespath.JMESPathDocumentQuery;
import software.amazon.smithy.jmespath.JmespathExpression;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Provides context parameters from operations using {@code smithy.rules#contextParam},
 * {@code smithy.rules#operationContextParams}, and {@code smithy.rules#staticContextParams} traits.
 *
 * <p>The results of finding operation context parameters from an operation are cached and reused over the life of
 * a client per/operation.
 */
sealed interface ContextProvider {

    void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params);

    final class OrchestratingProvider implements ContextProvider {
        private final ConcurrentMap<ShapeId, ContextProvider> providers = new ConcurrentHashMap<>();

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params) {
            var provider = providers.get(operation.schema().id());
            if (provider == null) {
                provider = createProvider(operation);
                var fresh = providers.putIfAbsent(operation.schema().id(), provider);
                if (fresh != null) {
                    provider = fresh;
                }
            }
            provider.addContext(operation, input, params);
        }

        private ContextProvider createProvider(ApiOperation<?, ?> operation) {
            List<ContextProvider> providers = new ArrayList<>(3);
            var operationSchema = operation.schema();
            var inputSchema = operation.inputSchema();
            ContextParamProvider.compute(providers, inputSchema);
            ContextPathProvider.compute(providers, operationSchema);
            StaticParamsProvider.compute(providers, operationSchema); // overrides everything else
            return MultiContextParamProvider.from(providers);
        }
    }

    // Find the smithy.rules#staticContextParams on the operation.
    record StaticParamsProvider(Map<String, Object> params) implements ContextProvider {
        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params) {
            params.putAll(this.params);
        }

        static void compute(List<ContextProvider> providers, Schema operation) {
            var staticParamsTrait = operation.getTrait(EndpointRulesPlugin.STATIC_CONTEXT_PARAMS_TRAIT);
            if (staticParamsTrait == null) {
                return;
            }

            Map<String, Object> result = new HashMap<>(staticParamsTrait.getParameters().size());
            for (var entry : staticParamsTrait.getParameters().entrySet()) {
                result.put(entry.getKey(), EndpointUtils.convertNode(entry.getValue().getValue()));
            }

            providers.add(new StaticParamsProvider(result));
        }
    }

    // Find smithy.rules#contextParam trait on operation input members.
    record ContextParamProvider(Schema member, String name) implements ContextProvider {
        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params) {
            var value = input.getMemberValue(member);
            if (value != null) {
                params.put(name, value);
            }
        }

        static void compute(List<ContextProvider> providers, Schema inputSchema) {
            for (var member : inputSchema.members()) {
                var ctxTrait = member.getTrait(EndpointRulesPlugin.CONTEXT_PARAM_TRAIT);
                if (ctxTrait != null) {
                    providers.add(new ContextParamProvider(member, ctxTrait.getName()));
                }
            }
        }
    }

    // Find the smithy.rules#operationContextParams trait on the operation and each JMESPath to extract.
    // TODO: Converting input to Document for JMESPath is expensive. Implementing something like JMESPathStructQuery
    //       that operates directly on SerializableStruct to avoid intermediate Document conversion could help.
    record ContextPathProvider(String name, JmespathExpression jp) implements ContextProvider {
        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params) {
            var doc = Document.of(input);
            var result = JMESPathDocumentQuery.query(jp, doc);
            if (result != null) {
                params.put(name, result.asObject());
            }
        }

        static void compute(List<ContextProvider> providers, Schema operation) {
            var params = operation.getTrait(EndpointRulesPlugin.OPERATION_CONTEXT_PARAMS_TRAIT);
            if (params == null) {
                return;
            }

            for (var param : params.getParameters().entrySet()) {
                var name = param.getKey();
                var path = param.getValue().getPath();
                var jp = JmespathExpression.parse(path);
                providers.add(new ContextPathProvider(name, jp));
            }
        }
    }

    // Applies multiple context providers.
    record MultiContextParamProvider(List<ContextProvider> providers) implements ContextProvider {
        static ContextProvider from(List<ContextProvider> providers) {
            return providers.size() == 1 ? providers.get(0) : new MultiContextParamProvider(providers);
        }

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params) {
            for (ContextProvider provider : providers) {
                provider.addContext(operation, input, params);
            }
        }
    }

    static void createEndpointParams(
            Map<String, Object> target,
            ContextProvider operationContextParams,
            Context context,
            ApiOperation<?, ?> operation,
            SerializableStruct input
    ) {
        operationContextParams.addContext(operation, input, target);
        var additionalParams = context.get(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS);
        if (additionalParams != null) {
            target.putAll(additionalParams);
        }
    }
}
