/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;

/**
 * Generates the implementation of the
 * {@link software.amazon.smithy.java.runtime.core.schema.SerializableShape#serialize(ShapeSerializer)}
 * method for a structure class.
 */
record StructureSerializerGenerator(
    JavaWriter writer,
    StructureShape shape,
    SymbolProvider symbolProvider,
    Model model,
    ServiceShape service
) implements Runnable {

    @Override
    public void run() {
        writer.pushState();
        var template = """
            @Override
            public void serialize(${shapeSerializer:N} serializer) {
                serializer.writeStruct($$SCHEMA, this);
            }

            @Override
            public void serializeMembers(${shapeSerializer:N} serializer) {
                ${writeMemberSerialization:C|}
            }
            """;
        writer.putContext("shapeSerializer", ShapeSerializer.class);
        writer.putContext("writeMemberSerialization", writer.consumer(this::writeMemberSerialization));
        writer.write(template);
        writer.popState();
    }

    private void writeMemberSerialization(JavaWriter writer) {
        boolean isError = shape.hasTrait(ErrorTrait.class);

        for (var member : shape.members()) {
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
                    || target.isStructureShape() || target.isUnionShape()
            );
            writer.putContext("memberName", memberName);
            writer.writeInline("""
                ${?nullable}if (${memberName:L} != null) {
                    ${/nullable}${C|};${?nullable}
                }${/nullable}
                """, new SerializerMemberGenerator(writer, symbolProvider, model, service, member, memberName));
            writer.popState();
        }
    }
}
