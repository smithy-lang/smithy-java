/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import static java.util.function.Predicate.not;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;

record StructureDeserializerGenerator(
    JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model, ServiceShape service
) implements Runnable {

    @Override
    public void run() {
        writer.pushState();
        var template = """
            @Override
            public Builder deserialize(${shapeDeserializer:N} decoder) {
                decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
                return this;
            }

            private static final class InnerDeserializer implements ${shapeDeserializer:T}.StructMemberConsumer<Builder> {
                private static final InnerDeserializer INSTANCE = new InnerDeserializer();

                @Override
                public void accept(Builder builder, ${sdkSchema:T} member, ${shapeDeserializer:T} de) {
                    ${?hasMembers}switch (member.memberIndex()) {
                        ${cases:C|}
                    }${/hasMembers}
                }${?union}

                @Override
                public void unknownMember(Builder builder, ${string:T} memberName) {
                    builder.$$unknownMember(memberName);
                }${/union}
            }""";
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.putContext("sdkSchema", Schema.class);
        writer.putContext("string", String.class);
        writer.putContext(
            "hasMembers",
            shape.members()
                .stream()
                .map(s -> model.expectShape(s.getTarget()))
                .anyMatch(not(t -> CodegenUtils.isStreamingBlob(t) || CodegenUtils.isEventStream(t)))
        );
        writer.putContext("cases", writer.consumer(this::generateMemberSwitchCases));
        writer.putContext("union", shape.isUnionShape());
        writer.write(template);
        writer.popState();
    }

    private void generateMemberSwitchCases(JavaWriter writer) {
        int idx = 0;
        for (var iter = CodegenUtils.getSortedMembers(shape).iterator(); iter.hasNext(); idx++) {
            var member = iter.next();
            var target = model.expectShape(member.getTarget());
            // skip data streams and event streams
            if (CodegenUtils.isStreamingBlob(target) || CodegenUtils.isEventStream(target)) {
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
