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
final class StructureSerializerGenerator implements Runnable {
    private final JavaWriter writer;
    private final SymbolProvider symbolProvider;
    private final Model model;
    private final StructureShape shape;
    private final ServiceShape service;

    public StructureSerializerGenerator(
        JavaWriter writer,
        SymbolProvider symbolProvider,
        Model model,
        StructureShape shape,
        ServiceShape service
    ) {
        this.writer = writer;
        this.symbolProvider = symbolProvider;
        this.model = model;
        this.shape = shape;
        this.service = service;
    }

    @Override
    public void run() {
        boolean isError = shape.hasTrait(ErrorTrait.class);
        for (var member : shape.members()) {
            var target = model.expectShape(member.getTarget());
            // Streaming blobs are not handled by deserialize method
            if (CodegenUtils.isStreamingBlob(target)) {
                continue;
            }

            var memberName = symbolProvider.toMemberName(member);
            // if the shape is an error we need to use the `getMessage()` method for message field.
            var stateName = isError && memberName.equals("message") ? "getMessage()" : memberName;
            var memberVisitor = new SerializerMemberGenerator(
                writer,
                symbolProvider,
                model,
                member,
                "serializer",
                "shape." + stateName,
                service
            );

            if (CodegenUtils.isNullableMember(member)) {
                // If the member is nullable, check for existence first.
                writer.write(
                    """
                        if (shape.$L != null) {
                            ${C|};
                        }""",
                    stateName,
                    memberVisitor
                );
            } else {
                writer.write("${C|};", memberVisitor);
            }
        }
    }
}
