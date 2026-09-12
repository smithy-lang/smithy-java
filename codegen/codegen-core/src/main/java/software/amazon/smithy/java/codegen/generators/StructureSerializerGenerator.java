/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ContextualDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;

/**
 * Generates the implementation of the
 * {@link SerializableShape#serialize(ShapeSerializer)}
 * method for a structure class.
 */
record StructureSerializerGenerator(
        ContextualDirective<CodeGenerationContext, ?> directive,
        JavaWriter writer,
        StructureShape shape,
        SymbolProvider symbolProvider,
        Model model) implements Runnable {

    @Override
    public void run() {
        writer.pushState();
        var template = """
                ${^isError}@Override
                public ${schemaClass:T} schema() {
                    return $$SCHEMA;
                }

                ${/isError}
                @Override
                public void serializeMembers(${shapeSerializer:T} serializer) {
                    ${writeMemberSerialization:C|}
                }
                ${?hasPresenceBits}
                @Override
                public long presenceBits() {
                    ${writePresenceBits:C|}
                }

                ${/hasPresenceBits}
                """;
        writer.putContext("shapeSerializer", ShapeSerializer.class);
        writer.putContext("writeMemberSerialization", writer.consumer(this::writeMemberSerialization));
        writer.putContext("writePresenceBits", writer.consumer(this::writePresenceBits));
        writer.putContext("schemaClass", Schema.class);
        // Bit 63 is reserved so PRESENCE_UNKNOWN (a negative value) can never collide with real bits.
        writer.putContext("hasPresenceBits", !shape.members().isEmpty() && shape.members().size() <= 63);
        writer.putContext("isError", shape.hasTrait(ErrorTrait.class));
        writer.write(template);
        writer.popState();
    }

    /**
     * Writes the body of {@code presenceBits()}: a constant for the members serializeMembers always emits,
     * OR'd with a bit per nullable member gated by the same null check serializeMembers uses. Bit i is the
     * member at position i of the sorted member list, which is the runtime memberIndex.
     */
    private void writePresenceBits(JavaWriter writer) {
        boolean isError = shape.hasTrait(ErrorTrait.class);
        long constantBits = 0;
        int index = 0;
        var nullableNames = new ArrayList<String>();
        var nullableBits = new ArrayList<Long>();
        for (var member : CodegenUtils.getSortedMembers(model, shape)) {
            var memberName = symbolProvider.toMemberName(member);
            if (isError && memberName.equalsIgnoreCase("message")) {
                memberName = "getMessage()";
            }
            var target = model.expectShape(member.getTarget());
            boolean nullable = CodegenUtils.isNullableMember(model, member)
                    || target.isStructureShape()
                    || target.isUnionShape();
            if (nullable) {
                nullableNames.add(memberName);
                nullableBits.add(1L << index);
            } else {
                constantBits |= 1L << index;
            }
            index++;
        }
        if (nullableNames.isEmpty()) {
            writer.write("return 0x$LL;", Long.toHexString(constantBits));
            return;
        }
        writer.write("long bits = 0x$LL;", Long.toHexString(constantBits));
        for (int i = 0; i < nullableNames.size(); i++) {
            writer.write("if ($L != null) {", nullableNames.get(i));
            writer.indent();
            writer.write("bits |= 0x$LL;", Long.toHexString(nullableBits.get(i)));
            writer.dedent();
            writer.write("}");
        }
        writer.write("return bits;");
    }

    private void writeMemberSerialization(JavaWriter writer) {
        boolean isError = shape.hasTrait(ErrorTrait.class);

        for (var member : CodegenUtils.getSortedMembers(model, shape)) {
            var memberName = symbolProvider.toMemberName(member);
            // if the shape is an error we need to use the `getMessage()` method for message field.
            if (isError && memberName.equalsIgnoreCase("message")) {
                memberName = "getMessage()";
            }

            var target = model.expectShape(member.getTarget());

            writer.pushState();
            writer.putContext(
                    "nullable",
                    CodegenUtils.isNullableMember(model, member)
                            || target.isStructureShape()
                            || target.isUnionShape());
            writer.putContext("memberName", memberName);
            writer.writeInline("""
                    ${?nullable}if (${memberName:L} != null) {
                        ${/nullable}${C|};${?nullable}
                    }${/nullable}
                    """, new SerializerMemberGenerator(directive, writer, member, memberName));
            writer.popState();
        }
    }
}
