/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NodeVisitor;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;
import software.amazon.smithy.model.traits.StringListTrait;
import software.amazon.smithy.model.traits.StringTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

record TraitInitializerGenerator(JavaWriter writer, Shape shape, Set<ShapeId> runtimeTraits) implements Runnable {

    private static final Map<ShapeId, Class<? extends TraitService>> serviceMap = new HashMap<>();
    static {
        // Add all trait services to a map, so they can be queried for a provider class
        ServiceLoader.load(TraitService.class, TraitInitializerGenerator.class.getClassLoader()).forEach((service) -> {
            serviceMap.put(service.getShapeId(), service.getClass());
        });
    }

    @Override
    public void run() {
        var traitsToAdd = shape.getAllTraits().keySet().stream().filter(runtimeTraits::contains).toList();
        if (traitsToAdd.isEmpty()) {
            return;
        }

        writer.newLine();
        writer.indent();
        writer.openBlock(".traits(", ")", () -> {
            var iter = traitsToAdd.iterator();
            while (iter.hasNext()) {
                var traitId = iter.next();
                switch (shape.getAllTraits().get(traitId)) {
                    case AnnotationTrait a -> writer.writeInline("new $T()", a.getClass());
                    case StringTrait st -> writer.writeInline("new $T($S)", st.getClass(), st.getValue());
                    case StringListTrait slt -> writer.writeInline(
                        "new $T($S, $T.NONE)",
                        slt.getClass(),
                        slt.getValues(),
                        SourceLocation.class
                    );
                    case Trait t -> traitFactoryInitializer(writer, t);
                }
                if (iter.hasNext()) {
                    writer.writeInline(",").newLine();
                }
            }
            writer.newLine();
        });
        writer.dedent();
    }

    private void traitFactoryInitializer(JavaWriter writer, Trait trait) {
        var traitProviderClass = serviceMap.get(trait.toShapeId());
        if (traitProviderClass == null) {
            throw new UnsupportedOperationException("Could not find trait provider for " + trait);
        }
        writer.pushState();
        writer.putContext("shapeId", ShapeId.class);
        writer.putContext("node", Node.class);
        if (traitProviderClass.isMemberClass()) {
            writer.putContext("enclosing", traitProviderClass.getEnclosingClass());
        }
        writer.writeInline(
            """
                new ${?enclosing}${enclosing:T}.$1L${/enclosing}${^enclosing}$2T${/enclosing}().createTrait(
                    ${shapeId:T}.from($3S),
                    ${4C|}
                )""",
            traitProviderClass.getSimpleName(),
            traitProviderClass,
            trait.toShapeId(),
            writer.consumer(w -> trait.toNode().accept(new NodeWriter(writer)))
        );
        writer.popState();
    }

    private record NodeWriter(JavaWriter writer) implements NodeVisitor<Void> {

        @Override
        public Void booleanNode(BooleanNode booleanNode) {
            writer.writeInline("${node:T}.from($L)", booleanNode.getValue());
            return null;
        }

        @Override
        public Void numberNode(NumberNode numberNode) {
            writer.writeInline("${node:T}.from($L)", numberNode.getValue());
            return null;
        }

        @Override
        public Void stringNode(StringNode stringNode) {
            writer.writeInline("${node:T}.from($S)", stringNode.getValue());
            return null;
        }

        @Override
        public Void objectNode(ObjectNode objectNode) {
            writer.write("${node:T}.objectNodeBuilder()");
            writer.indent();
            var memberWriter = new MemberNodeWriter(writer);
            for (var memberEntry : objectNode.getStringMap().entrySet()) {
                writer.write(
                    ".withMember($S, $C)",
                    memberEntry.getKey(),
                    (Runnable) () -> memberEntry.getValue().accept(memberWriter)
                );
            }
            writer.writeWithNoFormatting(".build()");
            writer.dedent();
            return null;
        }

        @Override
        public Void arrayNode(ArrayNode arrayNode) {
            writer.write("$T.builder()", ArrayNode.class);
            writer.indent();
            var memberWriter = new MemberNodeWriter(writer);
            for (var element : arrayNode.getElements()) {
                writer.write(".withValue($C)", (Runnable) () -> element.accept(memberWriter));
            }
            writer.writeWithNoFormatting(".build()");
            writer.dedent();

            return null;
        }

        @Override
        public Void nullNode(NullNode nullNode) {
            throw new CodegenException("Could not write null node");
        }
    }

    private record MemberNodeWriter(JavaWriter writer) implements NodeVisitor<Void> {

        @Override
        public Void booleanNode(BooleanNode booleanNode) {
            writer.writeInline("$L", booleanNode.getValue());
            return null;
        }

        @Override
        public Void nullNode(NullNode nullNode) {
            throw new CodegenException("Could not write value for null node.");
        }

        @Override
        public Void numberNode(NumberNode numberNode) {
            writer.writeInline("$L", numberNode.getValue());
            return null;
        }

        @Override
        public Void objectNode(ObjectNode objectNode) {
            writer.write("${node:T}.objectNodeBuilder()");
            writer.indent();
            for (var memberEntry : objectNode.getStringMap().entrySet()) {
                writer.write(
                    ".withMember($S, $C)",
                    memberEntry.getKey(),
                    (Runnable) () -> memberEntry.getValue().accept(this)
                );
            }
            writer.writeWithNoFormatting(".build()");
            writer.dedent();
            return null;
        }

        @Override
        public Void arrayNode(ArrayNode arrayNode) {
            writer.write("$T.builder", ArrayNode.class);
            writer.indent();
            for (var element : arrayNode.getElements()) {
                writer.write(".withValue($C)", (Runnable) () -> element.accept(this));
            }
            writer.writeWithNoFormatting(".build()");
            writer.dedent();
            return null;
        }

        @Override
        public Void stringNode(StringNode stringNode) {
            writer.write("$S", stringNode.getValue());
            return null;
        }
    }
}
