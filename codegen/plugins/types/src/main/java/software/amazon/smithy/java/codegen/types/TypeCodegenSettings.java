/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.selector.Selector;
import software.amazon.smithy.model.shapes.ShapeId;

final class TypeCodegenSettings {
    private static final String SHAPES = "shapes";
    private static final String SELECTOR = "selector";
    private static final String GENERATE_OPERATIONS = "generateOperations";
    private static final String RENAMES = "renames";
    private static final List<String> PROPERTIES = List.of(SHAPES, SELECTOR, GENERATE_OPERATIONS, RENAMES);

    /**
     * By default, all Structures, Enums, IntEnums, and Unions will be generated.
     */
    private static final Selector DEFAULT_SELECTOR = Selector.parse(":is(structure, union, enum, intEnum)");

    private final JavaCodegenSettings codegenSettings;
    private final boolean generateOperations;
    private final Selector selector;
    private final List<ShapeId> shapes;
    private final Map<ShapeId, String> renames;

    private TypeCodegenSettings(Builder builder) {
        this.generateOperations = builder.generateOperations;
        this.selector = Objects.requireNonNullElse(builder.selector, DEFAULT_SELECTOR);
        this.shapes = builder.shapes;
        this.renames = builder.renames;
        this.codegenSettings = builder.codegenSettings;
    }

    public JavaCodegenSettings codegenSettings() {
        return codegenSettings;
    }

    public boolean generateOperations() {
        return generateOperations;
    }

    public Selector selector() {
        return selector;
    }

    public List<ShapeId> shapes() {
        return shapes;
    }

    public Map<ShapeId, String> renames() {
        return renames;
    }

    static TypeCodegenSettings fromNode(ObjectNode settingsNode) {
        var builder = builder();
        settingsNode
            .getBooleanMember(GENERATE_OPERATIONS, builder::generateOperations)
            .getStringMember(SELECTOR, builder::selector)
            .getArrayMember(SHAPES, n -> n.expectStringNode().expectShapeId(), builder::shapes)
            .getObjectMember(RENAMES, n -> {
                for (Map.Entry<String, Node> entry : n.getStringMap().entrySet()) {
                    builder.rename(ShapeId.from(entry.getKey()), entry.getValue().expectStringNode().getValue());
                }
            });
        builder.codegenSettings(getCodegenSettings(settingsNode));
        return builder.build();
    }

    private static JavaCodegenSettings getCodegenSettings(ObjectNode node) {
        var nodeBuilder = node.toBuilder();

        // Copy source location
        nodeBuilder.sourceLocation(node.getSourceLocation());

        // Remove unused properties
        PROPERTIES.forEach(nodeBuilder::withoutMember);
        // Add the synthetic service
        nodeBuilder.withMember("service", SyntheticServiceTransform.SYNTHETIC_SERVICE_ID.toString());
        return JavaCodegenSettings.fromNode(nodeBuilder.build());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Selector selector = null;
        private final List<ShapeId> shapes = new ArrayList<>();
        private boolean generateOperations = false;
        private final Map<ShapeId, String> renames = new HashMap<>();
        private JavaCodegenSettings codegenSettings;

        private Builder() {}

        public Builder selector(String selector) {
            this.selector = Selector.parse(selector);
            return this;
        }

        public Builder shapes(List<ShapeId> shapeIds) {
            this.shapes.addAll(shapeIds);
            return this;
        }

        public Builder generateOperations(boolean generateOperations) {
            this.generateOperations = generateOperations;
            return this;
        }

        public Builder rename(ShapeId id, String rename) {
            this.renames.put(id, rename);
            return this;
        }

        public Builder codegenSettings(JavaCodegenSettings codegenSettings) {
            this.codegenSettings = codegenSettings;
            return this;
        }

        TypeCodegenSettings build() {
            return new TypeCodegenSettings(this);
        }
    }
}
