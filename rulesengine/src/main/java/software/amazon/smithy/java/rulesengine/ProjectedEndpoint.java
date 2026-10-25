/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Endpoint backed directly by an interned property shape and its per-resolution dynamic leaves.
 */
abstract sealed class ProjectedEndpoint implements Endpoint, RulesExtension.EndpointPropertyBinding.ResolutionValues {

    private final SmithyUri uri;
    private final EndpointShape shape;
    private List<EndpointAuthScheme> authSchemes;

    ProjectedEndpoint(SmithyUri uri, EndpointShape shape) {
        this.uri = uri;
        this.shape = shape;
        this.authSchemes = shape.constantAuthSchemes();
    }

    @Override
    public final SmithyUri uri() {
        return uri;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T property(Context.Key<T> property) {
        return (T) shape.property(property, this);
    }

    @Override
    public final Set<Context.Key<?>> properties() {
        return shape.properties();
    }

    @Override
    public final List<EndpointAuthScheme> authSchemes() {
        List<EndpointAuthScheme> result = authSchemes;
        if (result == null) {
            result = authSchemes = createAuthSchemes();
        }
        return result;
    }

    private List<EndpointAuthScheme> createAuthSchemes() {
        int count = shape.authSchemeCount();
        if (count == 0) {
            return List.of();
        }
        if (count == 1) {
            EndpointAuthScheme scheme = shape.authScheme(0, this);
            return scheme == null ? List.of() : List.of(scheme);
        }
        var result = new ArrayList<EndpointAuthScheme>(count);
        for (int i = 0; i < count; i++) {
            EndpointAuthScheme scheme = shape.authScheme(i, this);
            if (scheme != null) {
                result.add(scheme);
            }
        }
        return List.copyOf(result);
    }

    final Object resolve(PropertyPlan plan, int slotOffset) {
        return switch (plan) {
            case PropertyPlan.Constant constant -> constant.value();
            case PropertyPlan.DynamicSlot slot -> slot(slotOffset + slot.slot());
            default -> materialize(plan, slotOffset);
        };
    }

    abstract Object slot(int index);

    abstract Object materialize(PropertyPlan plan, int slotOffset);

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || other.getClass() != getClass()) {
            return false;
        }
        var endpoint = (ProjectedEndpoint) other;
        return uri.equals(endpoint.uri)
                && propertiesEqual(endpoint)
                && authSchemes().equals(endpoint.authSchemes());
    }

    @SuppressWarnings("unchecked")
    private boolean propertiesEqual(Endpoint endpoint) {
        if (!properties().equals(endpoint.properties())) {
            return false;
        }
        for (Context.Key<?> key : properties()) {
            if (!Objects.equals(property((Context.Key<Object>) key), endpoint.property(key))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + authSchemes().hashCode();
        for (Context.Key<?> key : properties()) {
            result += key.hashCode() ^ Objects.hashCode(property(key));
        }
        return result;
    }

    @Override
    public final String toString() {
        return "Endpoint{uri=" + uri + ", authSchemes=" + authSchemes() + ", properties=" + properties() + '}';
    }

    sealed interface EndpointShape permits EmptyShape, SingleBindingShape, MultiBindingShape {
        Set<Context.Key<?>> properties();

        int authSchemeCount();

        List<EndpointAuthScheme> constantAuthSchemes();

        Object property(Context.Key<?> property, RulesExtension.EndpointPropertyBinding.ResolutionValues values);

        EndpointAuthScheme authScheme(int index, RulesExtension.EndpointPropertyBinding.ResolutionValues values);
    }

    record EmptyShape() implements EndpointShape {
        @Override
        public Set<Context.Key<?>> properties() {
            return Set.of();
        }

        @Override
        public int authSchemeCount() {
            return 0;
        }

        @Override
        public List<EndpointAuthScheme> constantAuthSchemes() {
            return List.of();
        }

        @Override
        public Object property(
                Context.Key<?> property,
                RulesExtension.EndpointPropertyBinding.ResolutionValues values
        ) {
            return null;
        }

        @Override
        public EndpointAuthScheme authScheme(
                int index,
                RulesExtension.EndpointPropertyBinding.ResolutionValues values
        ) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    record SingleBindingShape(
            RulesExtension.EndpointPropertyBinding binding,
            Set<Context.Key<?>> properties,
            int authSchemeCount,
            List<EndpointAuthScheme> constantAuthSchemes) implements EndpointShape {
        @Override
        public Object property(
                Context.Key<?> property,
                RulesExtension.EndpointPropertyBinding.ResolutionValues values
        ) {
            return binding.property(property, values);
        }

        @Override
        public EndpointAuthScheme authScheme(
                int index,
                RulesExtension.EndpointPropertyBinding.ResolutionValues values
        ) {
            return binding.authScheme(index, values);
        }
    }

    record MultiBindingShape(
            RulesExtension.EndpointPropertyBinding[] bindings,
            Set<Context.Key<?>> properties,
            int authSchemeCount,
            List<EndpointAuthScheme> constantAuthSchemes) implements EndpointShape {
        @Override
        public Object property(
                Context.Key<?> property,
                RulesExtension.EndpointPropertyBinding.ResolutionValues values
        ) {
            for (int i = bindings.length - 1; i >= 0; i--) {
                RulesExtension.EndpointPropertyBinding binding = bindings[i];
                if (binding.properties().contains(property)) {
                    return binding.property(property, values);
                }
            }
            return null;
        }

        @Override
        public EndpointAuthScheme authScheme(
                int index,
                RulesExtension.EndpointPropertyBinding.ResolutionValues values
        ) {
            int target = index;
            for (RulesExtension.EndpointPropertyBinding binding : bindings) {
                int count = binding.authSchemeCount();
                if (target < count) {
                    return binding.authScheme(target, values);
                }
                target -= count;
            }
            throw new IndexOutOfBoundsException(index);
        }
    }

    static final class Endpoint0 extends ProjectedEndpoint {
        Endpoint0(SmithyUri uri, EndpointShape shape) {
            super(uri, shape);
        }

        @Override
        Object slot(int index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        Object materialize(PropertyPlan plan, int slotOffset) {
            return PropertyViews.materialize(plan, slotOffset);
        }
    }

    static final class Endpoint1 extends ProjectedEndpoint {
        private final Object value0;

        Endpoint1(SmithyUri uri, EndpointShape shape, Object value0) {
            super(uri, shape);
            this.value0 = value0;
        }

        @Override
        Object slot(int index) {
            if (index == 0) {
                return value0;
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        Object materialize(PropertyPlan plan, int slotOffset) {
            return PropertyViews.materialize(plan, slotOffset, value0);
        }
    }

    static final class Endpoint2 extends ProjectedEndpoint {
        private final Object value0;
        private final Object value1;

        Endpoint2(SmithyUri uri, EndpointShape shape, Object value0, Object value1) {
            super(uri, shape);
            this.value0 = value0;
            this.value1 = value1;
        }

        @Override
        Object slot(int index) {
            return switch (index) {
                case 0 -> value0;
                case 1 -> value1;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }

        @Override
        Object materialize(PropertyPlan plan, int slotOffset) {
            return PropertyViews.materialize(plan, slotOffset, value0, value1);
        }
    }

    static final class Endpoint3 extends ProjectedEndpoint {
        private final Object value0;
        private final Object value1;
        private final Object value2;

        Endpoint3(SmithyUri uri, EndpointShape shape, Object value0, Object value1, Object value2) {
            super(uri, shape);
            this.value0 = value0;
            this.value1 = value1;
            this.value2 = value2;
        }

        @Override
        Object slot(int index) {
            return switch (index) {
                case 0 -> value0;
                case 1 -> value1;
                case 2 -> value2;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }

        @Override
        Object materialize(PropertyPlan plan, int slotOffset) {
            return PropertyViews.materialize(plan, slotOffset, value0, value1, value2);
        }
    }

    static final class EndpointN extends ProjectedEndpoint {
        private final Object[] values;

        EndpointN(SmithyUri uri, EndpointShape shape, Object[] values) {
            super(uri, shape);
            this.values = values;
        }

        @Override
        Object slot(int index) {
            return values[index];
        }

        @Override
        Object materialize(PropertyPlan plan, int slotOffset) {
            return PropertyViews.materialize(plan, slotOffset, values);
        }
    }
}
