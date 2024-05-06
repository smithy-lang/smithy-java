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
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.ErrorTrait;

/**
 * Generates the implementation of the
 * {@link software.amazon.smithy.java.runtime.core.schema.SerializableShape#serialize(ShapeSerializer)}
 * method for a class.
 */
final class SerializerGenerator extends ShapeVisitor.Default<Void> implements Runnable {
    private final JavaWriter writer;
    private final SymbolProvider symbolProvider;
    private final Model model;
    private final Shape shape;
    private final ServiceShape service;

    public SerializerGenerator(
        JavaWriter writer,
        SymbolProvider symbolProvider,
        Model model,
        Shape shape,
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
        writer.pushState();
        shape.accept(this);
        writer.popState();
    }

    @Override
    protected Void getDefault(Shape shape) {
        throw new IllegalArgumentException("Illegal action.");
    }

    @Override
    public Void structureShape(StructureShape shape) {
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
                    memberName,
                    memberVisitor
                );
            } else {
                writer.write("${C|};", memberVisitor);
            }
        }
        return null;
    }

    @Override
    public Void listShape(ListShape shape) {
        writer.write(
            """
                for (var value : values) {
                    ${C|};
                }""",
            new SerializerMemberGenerator(
                writer,
                symbolProvider,
                model,
                shape.getMember(),
                "serializer",
                "value",
                service
            )
        );
        return null;
    }

    @Override
    public Void mapShape(MapShape shape) {
        var target = model.expectShape(shape.getValue().getTarget());
        writer.write(
            """
                for (var valueEntry : values.entrySet()) {
                    serializer.writeEntry(
                        $L,
                        valueEntry.getKey(),
                        valueEntry.getValue(),
                        ${C|}
                    );
                }""",
            CodegenUtils.getSchemaType(writer, symbolProvider, target),
            writer.consumer(w -> getValueSerializer(w, target, shape))
        );
        return null;
    }

    private void getValueSerializer(JavaWriter writer, Shape target, Shape root) {
        if (target.isListShape()) {
            writer.write(
                "SharedSchemas.$USerializer.INSTANCE",
                CodegenUtils.getDefaultName(target, service)
            );
        } else {
            writer.write(
                "SharedSchemas.$UValueSerializer.INSTANCE",
                CodegenUtils.getDefaultName(root, service)
            );
        }
    }
}
