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
    SymbolProvider symbolProvider,
    Model model,
    StructureShape shape,
    ServiceShape service
) implements Runnable {

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("shapeSerializer", ShapeSerializer.class);
        boolean isError = shape.hasTrait(ErrorTrait.class);
        writer.write("@Override");
        writer.openBlock("public void serializeMembers(${shapeSerializer:T} serializer) {", "}", () -> {
            for (var member : shape.members()) {
                var target = model.expectShape(member.getTarget());
                // Streaming blobs are not handled by deserialize method
                if (CodegenUtils.isStreamingBlob(target)) {
                    continue;
                }
                var memberName = symbolProvider.toMemberName(member);
                // if the shape is an error we need to use the `getMessage()` method for message field.
                var state = isError && memberName.equals("message") ? "getMessage()" : memberName;

                writer.pushState();
                writer.putContext("nullable", CodegenUtils.isNullableMember(member));
                writer.putContext("memberName", memberName);
                writer.write("""
                    ${?nullable}if (${memberName:L} != null) {
                        ${/nullable}${C|};${?nullable}
                    }${/nullable}
                    """, new SerializerMemberGenerator(writer, symbolProvider, model, service, member, state));
                writer.popState();
            }
        });
        writer.popState();
    }
}
