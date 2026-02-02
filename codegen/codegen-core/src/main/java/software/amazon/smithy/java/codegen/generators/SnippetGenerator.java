/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.DATE_TIME;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.EPOCH_SECONDS;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.HTTP_DATE;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NodeVisitor;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EnumValueTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates Java snippets.
 */
@SmithyInternalApi
public final class SnippetGenerator {

    private SnippetGenerator() {}

    /**
     * Generates Java code to initialize a shape based on a Node value.
     *
     * <p>This does not use the deserialize method, but rather directly initializes the
     * shape by converting the node value to Java code. This enables support for
     * non-structured shapes and enables more natural examples.
     *
     * <p>Imports, headers, and package declarations are omitted. To include those, use
     * {@link #writeShapeInitializer} instead.
     *
     * @param context The code generation context.
     * @param shape The shape to initialize.
     * @param value The value to initialize the shape with.
     * @return Returns a string containing Java code initializing the given shape with
     *         the given value.
     */
    public static String generateShapeInitializer(
            CodeGenerationContext context,
            Shape shape,
            Node value
    ) {
        var writer = new JavaWriter(context.settings(), context.settings().packageNamespace(), "Snippet.java");
        writeShapeInitializer(context, shape, value, writer);
        return writer.toContentString().strip();
    }

    /**
     * Writes Java code to initialize a shape based on a Node value.
     *
     * <p>This does not use the deserialize method, but rather directly initializes the
     * shape by converting the node value to Java code. This enables support for
     * non-structured shapes and enables more natural examples.
     *
     * <p>Imports, headers, and package declarations are included. To exclude those, use
     * {@link #generateShapeInitializer} instead.
     *
     * @param context The code generation context.
     * @param shape The shape to initialize.
     * @param value The value to initialize the shape with.
     * @param writer The writer to write to.
     */
    public static void writeShapeInitializer(
            CodeGenerationContext context,
            Shape shape,
            Node value,
            JavaWriter writer
    ) {
        var visitor = new ShapeSnippetVisitor(context, writer, value);
        shape.accept(visitor);
    }

    /**
     * Visitor that generates initializers for shapes based on a node value.
     */
    private static final class ShapeSnippetVisitor extends ShapeVisitor.DataShapeVisitor<Void> {
        private final CodeGenerationContext context;
        private final JavaWriter writer;
        private final Node value;

        ShapeSnippetVisitor(CodeGenerationContext context, JavaWriter writer, Node value) {
            this.context = context;
            this.writer = writer;
            this.value = value;
        }

        @Override
        public Void blobShape(BlobShape shape) {
            // We don't have the context to know if a shape should be nullable, but even
            // if we did there's cases where we would want to write out null anyway, such
            // as in examples that deliberately showcase that validation.
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            var stringValue = value.expectStringNode().getValue();

            // Blob values in smithy nodes are *supposed* to be base64 encoded strings,
            // but in practice they are sometimes just UTF8 strings. So here we check
            // to see which is the case and handle each.
            if (isBase64(stringValue)) {
                writer.writeInline(
                        "$T.ofBytes($T.getDecoder().decode($S))",
                        DataStream.class,
                        Base64.class,
                        stringValue);
            } else {
                writer.writeInline("$T.ofString($S)", DataStream.class, stringValue);
            }
            return null;
        }

        private boolean isBase64(String value) {
            // Java is lenient on padding, but we don't want to be.
            if (value.length() % 4 != 0) {
                return false;
            }
            try {
                Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        @Override
        public Void booleanShape(BooleanShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            writer.writeInline("$L", value.expectBooleanNode().getValue() ? "true" : "false");
            return null;
        }

        @Override
        public Void byteShape(ByteShape shape) {
            numericShape();
            return null;
        }

        @Override
        public Void shortShape(ShortShape shape) {
            numericShape();
            return null;
        }

        @Override
        public Void integerShape(IntegerShape shape) {
            numericShape();
            return null;
        }

        @Override
        public Void intEnumShape(IntEnumShape shape) {
            writeEnumShape(shape);
            return null;
        }

        private void writeEnumShape(Shape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return;
            }
            var symbol = context.symbolProvider().toSymbol(shape);
            var member = getMember(shape, value);
            if (member.isPresent()) {
                var memberName = context.symbolProvider().toMemberName(member.get());
                writer.writeInline("$T.$L", symbol, memberName);
            } else {
                if (shape.isEnumShape()) {
                    writer.writeInline("$T.unknown($S)", symbol, value.expectStringNode().getValue());
                } else {
                    writer.writeInline("$T.unknown($L)", symbol, value.expectNumberNode().getValue());
                }
            }

        }

        private Optional<MemberShape> getMember(Shape shape, Node value) {
            for (MemberShape member : shape.members()) {
                var trait = member.expectTrait(EnumValueTrait.class);
                if (trait.toNode().equals(value)) {
                    return Optional.of(member);
                }
            }
            return Optional.empty();
        }

        @Override
        public Void longShape(LongShape shape) {
            numericShape();
            return null;
        }

        @Override
        public Void floatShape(FloatShape shape) {
            numericShape();
            return null;
        }

        @Override
        public Void doubleShape(DoubleShape shape) {
            numericShape();
            return null;
        }

        private void numericShape() {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return;
            }
            writer.writeInline("$L", value.expectNumberNode().getValue());
        }

        @Override
        public Void bigIntegerShape(BigIntegerShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            var innerValue = value.asNumberNode()
                    .map(n -> n.getValue().toString())
                    .orElseGet(() -> value.expectStringNode().getValue());
            writer.writeInline("new $T($S)", BigInteger.class, innerValue);
            return null;
        }

        @Override
        public Void bigDecimalShape(BigDecimalShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            var innerValue = value.asNumberNode()
                    .map(n -> n.getValue().toString())
                    .orElseGet(() -> value.expectStringNode().getValue());
            writer.writeInline("new $T($S)", BigDecimal.class, innerValue);
            return null;
        }

        @Override
        public Void stringShape(StringShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            writer.writeInline("$S", value.expectStringNode().getValue());
            return null;
        }

        @Override
        public Void enumShape(EnumShape shape) {
            writeEnumShape(shape);
            return null;
        }

        @Override
        public Void timestampShape(TimestampShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            var instant = parseTimestampNode(value);
            writer.writeInline("$T.parse($S)", Instant.class, instant);
            return null;
        }

        // Note that we're not attempting to use the timestamp format trait to figure
        // out what the format is because the serialized format isn't relevant to the
        // format presented in the value node.
        private Instant parseTimestampNode(Node node) {
            if (node.isNumberNode()) {
                return EPOCH_SECONDS.readFromNumber(node.expectNumberNode().getValue());
            }

            var stringValue = node.expectStringNode().getValue();
            try {
                return DATE_TIME.readFromString(stringValue, false);
            } catch (DateTimeParseException e) {
                return HTTP_DATE.readFromString(stringValue, false);
            }
        }

        @Override
        public Void listShape(ListShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }

            var symbol = context.symbolProvider().toSymbol(shape);
            var listValue = value.expectArrayNode();
            if (listValue.isEmpty()) {
                var method = symbol.expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD);
                writer.writeInline("$T.$L", Collections.class, method);
                return null;
            }

            // Lists have the COLLECTION_IMPLEMENTATION_CLASS symbol property that defines
            // what class should be used by default in the deserialize methods. That isn't
            // checked here because these are primarily used for examples, and the extra
            // complexity would be a hindrance.
            if (listValue.size() == 1) {
                // If there's only one entry, we don't need to put in newlines
                writer.write("$T.of(${C|})", List.class, writer.consumer(w -> listMembers(shape, listValue)));
                return null;
            }

            writer.write(
                    """
                            $T.of(
                                ${C|}
                            )""",
                    List.class,
                    writer.consumer(w -> listMembers(shape, listValue)));
            return null;
        }

        private void listMembers(ListShape shape, ArrayNode listValue) {
            var member = shape.getMember();
            var iterator = listValue.getElements().iterator();
            while (iterator.hasNext()) {
                var element = iterator.next();
                member.accept(new ShapeSnippetVisitor(context, writer, element));
                if (iterator.hasNext()) {
                    writer.write(",");
                }
            }
        }

        @Override
        public Void mapShape(MapShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }

            var symbol = context.symbolProvider().toSymbol(shape);
            var mapValue = value.expectObjectNode();
            if (mapValue.isEmpty()) {
                var method = symbol.expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD);
                writer.writeInline("$T.$L", Collections.class, method);
                return null;
            }

            writer.pushState();

            // The more compact form of Map.of is limited to 10 entries, this context
            // variable is used to prefer the compact form when possible.
            writer.putContext("compact", mapValue.size() <= 10);
            var memberConsumer = writer.consumer(w -> mapMembers(shape, mapValue));

            // Maps have the COLLECTION_IMPLEMENTATION_CLASS symbol property that defines
            // what class should be used by default in the deserialize methods. That isn't
            // checked here for two reasons. First, these are primarily used for examples,
            // where the extra complexity would be a hindrance. But also, it may not be
            // possible to initialize these alternate implementations in a way that lets
            // them be useful. Notably, LinkedHashMap, the default implementation class,
            // would be difficult to initialize inline. Wrapping Map.of would defeat the
            // point, and using streams or some other workaround would be cumbersome.
            if (mapValue.size() == 1) {
                // If there's only one entry, we don't need to put in newlines
                writer.writeInline("$T.of(${C|})", Map.class, memberConsumer);
            } else {
                writer.writeInline("""
                        $T.of${^compact}Entries${/compact}(
                            ${C|}
                        )""",
                        Map.class,
                        memberConsumer);
            }

            writer.popState();
            return null;
        }

        private void mapMembers(MapShape shape, ObjectNode mapValue) {
            var keyShape = shape.getKey();
            var valueShape = shape.getValue();
            var iterator = mapValue.getMembers().entrySet().iterator();

            while (iterator.hasNext()) {
                var entry = iterator.next();

                // The key can't just be written out as a string, because *technically* it could be an enum.
                var keyVisitor = new ShapeSnippetVisitor(context, writer, entry.getKey());
                var keyConsumer = writer.consumer(w -> keyShape.accept(keyVisitor));

                var valueVisitor = new ShapeSnippetVisitor(context, writer, entry.getValue());
                var valueConsumer = writer.consumer(w -> valueShape.accept(valueVisitor));

                writer.writeInline("""
                        ${^compact}Map.entry(${/compact}\
                        ${C|}, ${C|}\
                        ${^compact})${/compact}""",
                        keyConsumer,
                        valueConsumer);

                if (iterator.hasNext()) {
                    writer.write(",");
                }
            }
        }

        @Override
        public Void structureShape(StructureShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            var symbol = context.symbolProvider().toSymbol(shape);
            writer.writeInline("""
                    $T.builder()
                        ${C|}
                        .build()
                    """, symbol, writer.consumer(w -> structureMembers(shape)));
            return null;
        }

        private void structureMembers(Shape shape) {
            ObjectNode structValue = value.expectObjectNode();
            for (Map.Entry<StringNode, Node> entry : structValue.getMembers().entrySet()) {
                var member = shape.getMember(entry.getKey().getValue())
                        .orElseThrow(() -> new CodegenException(String.format(
                                "Tried to bind key \"%s\" to a member of %s, but no matching member name was found.",
                                entry.getKey().getValue(),
                                shape.getId())));
                var memberName = context.symbolProvider().toMemberName(member);
                var memberVisitor = new ShapeSnippetVisitor(context, writer, entry.getValue());
                writer.writeInline(".$L(${C|})",
                        memberName,
                        writer.consumer(w -> member.accept(memberVisitor)));
            }
        }

        @Override
        public Void unionShape(UnionShape shape) {
            if (value.isNullNode()) {
                writer.writeInline("null");
                return null;
            }
            var symbol = context.symbolProvider().toSymbol(shape);

            var members = value.expectObjectNode().getMembers();
            if (members.size() != 1) {
                throw new CodegenException("Union value must have exactly one member, but found " + members.size());
            }

            var member = members.entrySet().iterator().next();
            var memberName = member.getKey().getValue();
            var memberShape = shape.getMember(memberName);

            if (memberShape.isEmpty()) {
                writer.writeInline("$T.$$Unknown($S)", symbol, memberName);
                return null;
            }
            memberName = context.symbolProvider().toMemberName(memberShape.get());

            var memberVisitor = new ShapeSnippetVisitor(context, writer, member.getValue());
            writer.writeInline("$T.$UMember(${C|})",
                    symbol,
                    memberName,
                    writer.consumer(w -> memberShape.get().accept(memberVisitor)));
            return null;
        }

        @Override
        public Void documentShape(DocumentShape shape) {
            value.accept(new DocumentSnippetVisitor(writer));
            return null;
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return context.model().expectShape(shape.getTarget()).accept(this);
        }
    }

    /**
     * Visitor that generates an initializer for a Document based on a node value.
     */
    private static final class DocumentSnippetVisitor implements NodeVisitor<Void> {

        private final JavaWriter writer;

        DocumentSnippetVisitor(JavaWriter writer) {
            this.writer = writer;
        }

        @Override
        public Void booleanNode(BooleanNode node) {
            writer.writeInline("$T.of($L)", Document.class, node.expectBooleanNode().getValue());
            return null;
        }

        @Override
        public Void nullNode(NullNode node) {
            writer.writeInline("null");
            return null;
        }

        @Override
        public Void numberNode(NumberNode node) {
            writer.writeInline("$T.of($L)", Document.class, node.expectNumberNode().getValue());
            return null;
        }

        @Override
        public Void stringNode(StringNode node) {
            writer.writeInline("$T.of($S)", Document.class, node.expectStringNode().getValue());
            return null;
        }

        @Override
        public Void arrayNode(ArrayNode node) {
            if (node.isEmpty()) {
                writer.writeInline("$T.of($T.emptyList())", Document.class, Collections.class);
                return null;
            }
            if (node.size() == 1) {
                writer.writeInline("$T.of($T.of(${C|}))",
                        Document.class,
                        List.class,
                        writer.consumer(w -> arrayMembers(node)));
            } else {
                writer.writeInline(
                        """
                                $T.of($T.of(
                                    ${C|}
                                ))""",
                        Document.class,
                        List.class,
                        writer.consumer(w -> arrayMembers(node)));
            }

            return null;
        }

        private void arrayMembers(ArrayNode listValue) {
            var iterator = listValue.getElements().iterator();
            while (iterator.hasNext()) {
                var element = iterator.next();
                element.accept(this);
                if (iterator.hasNext()) {
                    writer.write(",");
                }
            }
        }

        @Override
        public Void objectNode(ObjectNode node) {
            if (node.isEmpty()) {
                writer.writeInline("$T.of($T.emptyMap())", Document.class, Collections.class);
                return null;
            }

            writer.pushState();

            // The more compact form of Map.of is limited to 10 entries, this context
            // variable is used to prefer the compact form when possible.
            writer.putContext("compact", node.size() <= 10);
            var memberConsumer = writer.consumer(w -> objectMembers(node));

            if (node.size() == 1) {
                writer.writeInline("$T.of($T.of(${C|}))", Document.class, Map.class, memberConsumer);
            } else {
                writer.writeInline("""
                        $T.of($T.of${^compact}Entries${/compact}(
                            ${C|}
                        ))""",
                        Document.class,
                        Map.class,
                        memberConsumer);
            }

            writer.popState();
            return null;
        }

        private void objectMembers(ObjectNode node) {
            var iterator = node.getMembers().entrySet().iterator();

            while (iterator.hasNext()) {
                var entry = iterator.next();

                writer.writeInline("""
                        ${^compact}Map.entry(${/compact}\
                        $S, ${C|}\
                        ${^compact})${/compact}""",
                        entry.getKey().getValue(),
                        writer.consumer(w -> entry.getValue().accept(this)));

                if (iterator.hasNext()) {
                    writer.write(",");
                }
            }
        }
    }
}
