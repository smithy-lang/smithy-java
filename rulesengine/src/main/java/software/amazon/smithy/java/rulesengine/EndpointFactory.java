/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;
import software.amazon.smithy.java.endpoints.EndpointContext;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Resolver-local, load-time compilation of endpoint result properties.
 *
 * <p>Every result gets a direct array entry, and results with the same interned {@link PropertyPlan}
 * share their extension bindings. Resolution therefore performs neither synchronization nor a map
 * lookup before constructing the endpoint.
 */
final class EndpointFactory {
    private static final RulesExtension.EndpointPropertyBinding.ResolutionValues CONSTANT_VALUES =
            new RulesExtension.EndpointPropertyBinding.ResolutionValues() {};
    private static final Set<Context.Key<?>> HEADER_PROPERTIES = Set.of(EndpointContext.HEADERS);

    private final RulesExtension[] extensions;
    private final ResultPlan[] results;

    EndpointFactory(Bytecode bytecode, RulesExtension[] extensions) {
        this.extensions = extensions;
        this.results = new ResultPlan[bytecode.getResultCount()];

        var bindingsByShape = new IdentityHashMap<PropertyPlan, BindingGroup>();
        var materializedShapes = Collections.newSetFromMap(new IdentityHashMap<PropertyPlan, Boolean>());
        for (int i = 0; i < results.length; i++) {
            CompiledNode node = bytecode.resultNode(i);
            if (node instanceof CompiledNode.ReturnEndpoint endpoint) {
                results[i] = projected(endpoint, extensions, bindingsByShape, materializedShapes);
            }
        }
    }

    Endpoint create(int resultIndex, SmithyUri uri, BytecodeEvaluator evaluator) {
        return results[resultIndex].create(uri, evaluator);
    }

    private ResultPlan projected(
            CompiledNode.ReturnEndpoint endpoint,
            RulesExtension[] extensions,
            IdentityHashMap<PropertyPlan, BindingGroup> bindingsByShape,
            Set<PropertyPlan> materializedShapes
    ) {
        PropertyPlan.Compiled properties = endpoint.properties();
        PropertyPlan.Compiled headers = endpoint.headers();
        if (materializedShapes.contains(properties.plan())) {
            return materialized(endpoint);
        }
        BindingGroup group = bindingsByShape.get(properties.plan());
        if (group == null) {
            group = bind(properties, extensions);
            if (group == null) {
                materializedShapes.add(properties.plan());
                return materialized(endpoint);
            }
            bindingsByShape.put(properties.plan(), group);
        }

        ProjectedEndpoint.EndpointShape shape = headers.isConstantEmptyMap()
                ? group.withoutHeaders()
                : withHeaders(group, properties.slotCount(), headers);

        int propertySlots = properties.slotCount();
        int totalSlots = propertySlots + headers.slotCount();
        return switch (totalSlots) {
            case 0 -> new Projected0(shape);
            case 1 -> new Projected1(shape, dynamic(properties, headers, propertySlots, 0));
            case 2 -> new Projected2(
                    shape,
                    dynamic(properties, headers, propertySlots, 0),
                    dynamic(properties, headers, propertySlots, 1));
            case 3 -> new Projected3(
                    shape,
                    dynamic(properties, headers, propertySlots, 0),
                    dynamic(properties, headers, propertySlots, 1),
                    dynamic(properties, headers, propertySlots, 2));
            default -> {
                var dynamics = new CompiledNode[totalSlots];
                for (int i = 0; i < totalSlots; i++) {
                    dynamics[i] = dynamic(properties, headers, propertySlots, i);
                }
                yield new ProjectedN(shape, dynamics);
            }
        };
    }

    private BindingGroup bind(
            PropertyPlan.Compiled properties,
            RulesExtension[] extensions
    ) {
        var bindings = new RulesExtension.EndpointPropertyBinding[extensions.length];
        var keys = new HashSet<Context.Key<?>>();
        int authSchemeCount = 0;
        var propertyShape = new EndpointPropertyShape(properties.plan());
        for (int i = 0; i < extensions.length; i++) {
            RulesExtension.EndpointPropertyBinding binding =
                    extensions[i].bindEndpointProperties(propertyShape);
            if (binding == null) {
                return null;
            }
            bindings[i] = binding;
            keys.addAll(binding.properties());
            authSchemeCount += binding.authSchemeCount();
        }

        Set<Context.Key<?>> propertyKeys = Set.copyOf(keys);
        List<EndpointAuthScheme> constantAuthSchemes = properties.slotCount() == 0
                ? constantAuthSchemes(bindings, authSchemeCount)
                : null;
        ProjectedEndpoint.EndpointShape shape =
                shape(bindings, propertyKeys, authSchemeCount, constantAuthSchemes);
        return new BindingGroup(bindings, propertyKeys, authSchemeCount, constantAuthSchemes, shape);
    }

    private static ProjectedEndpoint.EndpointShape withHeaders(
            BindingGroup group,
            int slotOffset,
            PropertyPlan.Compiled headers
    ) {
        var bindings = new RulesExtension.EndpointPropertyBinding[group.bindings().length + 1];
        bindings[0] = new HeaderBinding(new EndpointPropertyShape(headers.plan(), slotOffset));
        System.arraycopy(group.bindings(), 0, bindings, 1, group.bindings().length);

        var keys = new HashSet<>(group.properties());
        keys.add(EndpointContext.HEADERS);
        return shape(
                bindings,
                Set.copyOf(keys),
                group.authSchemeCount(),
                group.constantAuthSchemes());
    }

    private static ProjectedEndpoint.EndpointShape shape(
            RulesExtension.EndpointPropertyBinding[] bindings,
            Set<Context.Key<?>> properties,
            int authSchemeCount,
            List<EndpointAuthScheme> constantAuthSchemes
    ) {
        return switch (bindings.length) {
            case 0 -> new ProjectedEndpoint.EmptyShape();
            case 1 -> new ProjectedEndpoint.SingleBindingShape(
                    bindings[0],
                    properties,
                    authSchemeCount,
                    constantAuthSchemes);
            default -> new ProjectedEndpoint.MultiBindingShape(
                    bindings,
                    properties,
                    authSchemeCount,
                    constantAuthSchemes);
        };
    }

    private static CompiledNode dynamic(
            PropertyPlan.Compiled properties,
            PropertyPlan.Compiled headers,
            int propertySlots,
            int index
    ) {
        return index < propertySlots
                ? properties.dynamic(index)
                : headers.dynamic(index - propertySlots);
    }

    private ResultPlan materialized(CompiledNode.ReturnEndpoint endpoint) {
        return new Materialized(endpoint.properties(), endpoint.headers(), extensions);
    }

    private static List<EndpointAuthScheme> constantAuthSchemes(
            RulesExtension.EndpointPropertyBinding[] bindings,
            int count
    ) {
        if (count == 0) {
            return List.of();
        }
        if (count == 1) {
            for (RulesExtension.EndpointPropertyBinding binding : bindings) {
                if (binding.authSchemeCount() == 1) {
                    EndpointAuthScheme scheme = binding.authScheme(0, CONSTANT_VALUES);
                    return scheme == null ? List.of() : List.of(scheme);
                }
            }
        }
        var result = new ArrayList<EndpointAuthScheme>(count);
        for (RulesExtension.EndpointPropertyBinding binding : bindings) {
            for (int i = 0; i < binding.authSchemeCount(); i++) {
                EndpointAuthScheme scheme = binding.authScheme(i, CONSTANT_VALUES);
                if (scheme != null) {
                    result.add(scheme);
                }
            }
        }
        return List.copyOf(result);
    }

    private record BindingGroup(
            RulesExtension.EndpointPropertyBinding[] bindings,
            Set<Context.Key<?>> properties,
            int authSchemeCount,
            List<EndpointAuthScheme> constantAuthSchemes,
            ProjectedEndpoint.EndpointShape withoutHeaders) {}

    private record HeaderBinding(Properties headers)
            implements RulesExtension.EndpointPropertyBinding {
        @Override
        public Set<Context.Key<?>> properties() {
            return HEADER_PROPERTIES;
        }

        @Override
        public Object property(
                Context.Key<?> property,
                ResolutionValues values
        ) {
            return property == EndpointContext.HEADERS ? headers.resolve(values) : null;
        }
    }

    private sealed interface ResultPlan
            permits Materialized, Projected0, Projected1, Projected2, Projected3, ProjectedN {
        Endpoint create(SmithyUri uri, BytecodeEvaluator evaluator);
    }

    private record Materialized(
            PropertyPlan.Compiled properties,
            PropertyPlan.Compiled headers,
            RulesExtension[] extensions) implements ResultPlan {
        @Override
        @SuppressWarnings("unchecked")
        public Endpoint create(SmithyUri uri, BytecodeEvaluator evaluator) {
            var props = (Map<String, Object>) properties.resolve(evaluator);
            var hdrs = (Map<String, List<String>>) headers.resolve(evaluator);
            var builder = Endpoint.builder().uri(uri);
            if (!hdrs.isEmpty()) {
                builder.putProperty(EndpointContext.HEADERS, hdrs);
            }
            for (RulesExtension extension : extensions) {
                extension.extractEndpointProperties(builder, evaluator.context, props, hdrs);
            }
            return builder.build();
        }
    }

    private record Projected0(ProjectedEndpoint.EndpointShape shape) implements ResultPlan {
        @Override
        public Endpoint create(SmithyUri uri, BytecodeEvaluator evaluator) {
            return new ProjectedEndpoint.Endpoint0(uri, shape);
        }
    }

    private record Projected1(
            ProjectedEndpoint.EndpointShape shape,
            CompiledNode dynamic0) implements ResultPlan {
        @Override
        public Endpoint create(SmithyUri uri, BytecodeEvaluator evaluator) {
            return new ProjectedEndpoint.Endpoint1(uri, shape, dynamic0.eval(evaluator));
        }
    }

    private record Projected2(
            ProjectedEndpoint.EndpointShape shape,
            CompiledNode dynamic0,
            CompiledNode dynamic1) implements ResultPlan {
        @Override
        public Endpoint create(SmithyUri uri, BytecodeEvaluator evaluator) {
            Object value0 = dynamic0.eval(evaluator);
            Object value1 = dynamic1.eval(evaluator);
            return new ProjectedEndpoint.Endpoint2(uri, shape, value0, value1);
        }
    }

    private record Projected3(
            ProjectedEndpoint.EndpointShape shape,
            CompiledNode dynamic0,
            CompiledNode dynamic1,
            CompiledNode dynamic2) implements ResultPlan {
        @Override
        public Endpoint create(SmithyUri uri, BytecodeEvaluator evaluator) {
            Object value0 = dynamic0.eval(evaluator);
            Object value1 = dynamic1.eval(evaluator);
            Object value2 = dynamic2.eval(evaluator);
            return new ProjectedEndpoint.Endpoint3(uri, shape, value0, value1, value2);
        }
    }

    private record ProjectedN(
            ProjectedEndpoint.EndpointShape shape,
            CompiledNode[] dynamics) implements ResultPlan {
        @Override
        public Endpoint create(SmithyUri uri, BytecodeEvaluator evaluator) {
            var values = new Object[dynamics.length];
            for (int i = 0; i < dynamics.length; i++) {
                values[i] = dynamics[i].eval(evaluator);
            }
            return new ProjectedEndpoint.EndpointN(uri, shape, values);
        }
    }

    /**
     * Extension view of a compiled endpoint property shape.
     */
    static final class EndpointPropertyShape implements RulesExtension.EndpointPropertyBinding.Properties {
        private final PropertyPlan plan;
        private final int slotOffset;

        EndpointPropertyShape(PropertyPlan plan) {
            this(plan, 0);
        }

        EndpointPropertyShape(PropertyPlan plan, int slotOffset) {
            this.plan = plan;
            this.slotOffset = slotOffset;
        }

        @Override
        public RulesExtension.EndpointPropertyBinding.Properties get(String name) {
            PropertyPlan child = switch (plan) {
                case PropertyPlan.MapShape map -> named(name, map.keys(), map.values());
                case PropertyPlan.StructShape struct -> named(name, struct.keys(), struct.values());
                case PropertyPlan.Constant constant -> constantNamed(constant.value(), name);
                default -> null;
            };
            return child == null ? null : new EndpointPropertyShape(child, slotOffset);
        }

        @Override
        public RulesExtension.EndpointPropertyBinding.Properties get(int index) {
            PropertyPlan child = switch (plan) {
                case PropertyPlan.ListShape list ->
                    index >= 0 && index < list.elements().length ? list.elements()[index] : null;
                case PropertyPlan.Constant constant -> constantIndexed(constant.value(), index);
                default -> null;
            };
            return child == null ? null : new EndpointPropertyShape(child, slotOffset);
        }

        @Override
        public boolean isObject() {
            return switch (plan) {
                case PropertyPlan.MapShape ignored -> true;
                case PropertyPlan.StructShape ignored -> true;
                case PropertyPlan.Constant constant ->
                    constant.value() instanceof Map<?, ?> || constant.value() instanceof PropertyGetter;
                default -> false;
            };
        }

        @Override
        public boolean isList() {
            return plan instanceof PropertyPlan.ListShape
                    || plan instanceof PropertyPlan.Constant constant && constant.value() instanceof List<?>;
        }

        @Override
        public int size() {
            return switch (plan) {
                case PropertyPlan.MapShape map -> map.values().length;
                case PropertyPlan.ListShape list -> list.elements().length;
                case PropertyPlan.StructShape struct -> struct.values().length;
                case PropertyPlan.Constant constant -> constantSize(constant.value());
                default -> 0;
            };
        }

        @Override
        public boolean isConstant() {
            return plan instanceof PropertyPlan.Constant;
        }

        @Override
        public Object constantValue() {
            if (plan instanceof PropertyPlan.Constant constant) {
                return constant.value();
            }
            throw new IllegalStateException("Endpoint property is dynamic");
        }

        @Override
        public Object resolve(RulesExtension.EndpointPropertyBinding.ResolutionValues values) {
            if (plan instanceof PropertyPlan.Constant constant) {
                return constant.value();
            }
            if (!(values instanceof ProjectedEndpoint endpoint)) {
                throw new IllegalArgumentException("Endpoint property values were not created by the rules engine");
            }
            return endpoint.resolve(plan, slotOffset);
        }

        private static PropertyPlan named(String name, String[] keys, PropertyPlan[] values) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(name)) {
                    return values[i];
                }
            }
            return null;
        }

        private static PropertyPlan constantNamed(Object value, String name) {
            Object child = switch (value) {
                case Map<?, ?> map -> map.get(name);
                case PropertyGetter getter -> getter.getProperty(name);
                default -> null;
            };
            return child == null ? null : new PropertyPlan.Constant(child);
        }

        private static PropertyPlan constantIndexed(Object value, int index) {
            if (value instanceof List<?> list && index >= 0 && index < list.size()) {
                return new PropertyPlan.Constant(list.get(index));
            }
            return null;
        }

        private static int constantSize(Object value) {
            return switch (value) {
                case Map<?, ?> map -> map.size();
                case List<?> list -> list.size();
                default -> 0;
            };
        }
    }
}
