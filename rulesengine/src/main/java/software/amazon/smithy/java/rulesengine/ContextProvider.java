/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

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
import software.amazon.smithy.java.jmespath.JmesPathQueries;
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

    final class RegisterSink {
        private final Object[] values;
        final Map<String, Integer> registerMap;
        long filled;
        private final int registerCount;

        RegisterSink(int registerCount, Map<String, Integer> registerMap) {
            this.values = new Object[registerCount];
            this.registerMap = registerMap;
            this.registerCount = registerCount;
        }

        void put(String name, Object value) {
            Integer i = registerMap.get(name);
            if (i != null) {
                values[i] = value;
                if (i < 64) {
                    filled |= 1L << i;
                }
            }
        }

        void putAll(Map<String, Object> map) {
            for (var e : map.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }

        /** Copy staged values into the target register array, then reset. */
        void drainTo(Object[] registers) {
            // Fast path: use bitmask for registers 0-63
            long mask = filled;
            while (mask != 0) {
                int i = Long.numberOfTrailingZeros(mask);
                registers[i] = values[i];
                values[i] = null;
                mask &= mask - 1;
            }
            filled = 0;
            // Slow path: scan registers 64+ for non-null values
            for (int i = 64; i < registerCount; i++) {
                if (values[i] != null) {
                    registers[i] = values[i];
                    values[i] = null;
                }
            }
        }
    }

    default void addContext(ApiOperation<?, ?> operation, SerializableStruct input, RegisterSink sink) {
        // Default: fall back to Map-based path for providers that haven't been updated.
        // This allocates a temporary map, but only for DecisionTree-style providers.
        var tmp = new HashMap<String, Object>();
        addContext(operation, input, tmp);
        sink.putAll(tmp);
    }

    final class OrchestratingProvider implements ContextProvider {
        private final ConcurrentMap<ShapeId, ContextProvider> providers = new ConcurrentHashMap<>();

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params) {
            getProvider(operation).addContext(operation, input, params);
        }

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, RegisterSink sink) {
            getProvider(operation).addContext(operation, input, sink);
        }

        private ContextProvider getProvider(ApiOperation<?, ?> operation) {
            var provider = providers.get(operation.schema().id());
            if (provider == null) {
                provider = createProvider(operation);
                var fresh = providers.putIfAbsent(operation.schema().id(), provider);
                if (fresh != null) {
                    provider = fresh;
                }
            }
            return provider;
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

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, RegisterSink sink) {
            sink.putAll(this.params);
        }

        static void compute(List<ContextProvider> providers, Schema operation) {
            var staticParamsTrait = operation.getTrait(RulesEngineTraits.STATIC_CONTEXT_PARAMS_TRAIT);
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

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, RegisterSink sink) {
            var value = input.getMemberValue(member);
            if (value != null) {
                sink.put(name, value);
            }
        }

        static void compute(List<ContextProvider> providers, Schema inputSchema) {
            for (var member : inputSchema.members()) {
                var ctxTrait = member.getTrait(RulesEngineTraits.CONTEXT_PARAM_TRAIT);
                if (ctxTrait != null) {
                    providers.add(new ContextParamProvider(member, ctxTrait.getName()));
                }
            }
        }
    }

    // Find the smithy.rules#operationContextParams trait on the operation and each JMESPath to extract.
    record ContextPathProvider(String name, JmespathExpression jp) implements ContextProvider {
        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, Map<String, Object> params) {
            var result = evaluateJmesPath(input);
            if (result != null) {
                params.put(name, result);
            }
        }

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, RegisterSink sink) {
            var result = evaluateJmesPath(input);
            if (result != null) {
                sink.put(name, result);
            }
        }

        private Object evaluateJmesPath(SerializableStruct input) {
            if (input instanceof Document doc) {
                var docResult = JmesPathQueries.query(jp, doc);
                return docResult != null ? docResult.asObject() : null;
            }
            return JmesPathQueries.query(jp, input);
        }

        static void compute(List<ContextProvider> providers, Schema operation) {
            var params = operation.getTrait(RulesEngineTraits.OPERATION_CONTEXT_PARAMS_TRAIT);
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

        @Override
        public void addContext(ApiOperation<?, ?> operation, SerializableStruct input, RegisterSink sink) {
            for (ContextProvider provider : providers) {
                provider.addContext(operation, input, sink);
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
        var additionalParams = context.get(RulesEngineSettings.ADDITIONAL_ENDPOINT_PARAMS);
        if (additionalParams != null) {
            target.putAll(additionalParams);
        }
    }

    static void createEndpointParams(
            RegisterSink sink,
            ContextProvider operationContextParams,
            Context context,
            ApiOperation<?, ?> operation,
            SerializableStruct input
    ) {
        operationContextParams.addContext(operation, input, sink);
        var additionalParams = context.get(RulesEngineSettings.ADDITIONAL_ENDPOINT_PARAMS);
        if (additionalParams != null) {
            sink.putAll(additionalParams);
        }
    }
}
