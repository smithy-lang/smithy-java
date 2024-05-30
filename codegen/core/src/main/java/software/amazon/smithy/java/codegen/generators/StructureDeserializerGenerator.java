/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 * TODO: docs
 */
record StructureDeserializerGenerator(
    JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model, ServiceShape service
) implements Runnable {
    @Override
    public void run() {
        writer.pushState();
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.putContext("sdkSchema", SdkSchema.class);
        writer.putContext("hasMembers", !shape.members().isEmpty());
        writer.write(
            """
                @Override
                public Builder deserialize(${shapeDeserializer:T} decoder) {
                    decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
                    return this;
                }

                private static final class InnerDeserializer implements ${shapeDeserializer:T}.StructMemberConsumer<Builder> {
                    private static final InnerDeserializer INSTANCE = new InnerDeserializer();

                    @Override
                    public void accept(Builder builder, ${sdkSchema:T} member, ${shapeDeserializer:T} de) {
                        ${?hasMembers}switch (member.memberIndex()) {
                            ${C|}
                        }${/hasMembers}
                    }
                }
                """,
            (Runnable) this::generateMemberSwitchCases
        );
        writer.popState();
    }

    private void generateMemberSwitchCases() {
        int idx = 0;
        for (var iter = CodegenUtils.getSortedMembers(shape).iterator(); iter.hasNext(); idx++) {
            var member = iter.next();
            var target = model.expectShape(member.getTarget());
            if (CodegenUtils.isStreamingBlob(target)) {
                // Streaming blobs are not deserialized by the builder class.
                continue;
            }

            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(member));
            writer.write(
                "case $L -> builder.${memberName:L}($C);",
                idx,
                new DeserializerGenerator(writer, member, symbolProvider, model, service, "de", "member")
            );
            writer.popState();
        }
    }
}
