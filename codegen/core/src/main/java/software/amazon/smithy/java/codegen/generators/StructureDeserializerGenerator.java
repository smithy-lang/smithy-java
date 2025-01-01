/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.ErrorTrait;

record StructureDeserializerGenerator(
        JavaWriter writer,
        Shape shape,
        SymbolProvider symbolProvider,
        Model model,
        ServiceShape service) implements Runnable {

    @Override
    public void run() {
        writer.pushState();
        var template =
                """
                        @Override
                        public Builder deserialize(${shapeDeserializer:N} decoder) {${?isError}
                            this.$$deserialized = true;${/isError}
                            decoder.readStruct($$SCHEMA, this, $$InnerDeserializer.INSTANCE);${?hasChecks}
                            this.disableChecks = true;${/hasChecks}
                            return this;
                        }

                        @Override
                        public Builder deserializeMember(${shapeDeserializer:N} decoder, ${sdkSchema:N} schema) {
                            decoder.readStruct(schema.assertMemberTargetIs($$SCHEMA), this, $$InnerDeserializer.INSTANCE);${?hasChecks}
                            this.disableChecks = true;${/hasChecks}
                            return this;
                        }

                        private static final class $$InnerDeserializer implements ${shapeDeserializer:T}.StructMemberConsumer<Builder> {
                            private static final $$InnerDeserializer INSTANCE = new $$InnerDeserializer();

                            @Override
                            public void accept(Builder builder, ${sdkSchema:T} member, ${shapeDeserializer:T} de) {${?hasMembers}
                                switch (member.memberIndex()) {
                                    ${cases:C|}
                                    default -> throw new ${illegalArg:T}("Unexpected member: " + member.memberName());
                                }
                            ${/hasMembers}}${?union}

                            @Override
                            public void unknownMember(Builder builder, ${string:T} memberName) {
                                builder.$$unknownMember(memberName);
                            }${/union}
                        }""";
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.putContext("sdkSchema", Schema.class);
        writer.putContext("string", String.class);
        writer.putContext("hasMembers", !shape.members().isEmpty());
        writer.putContext("cases", writer.consumer(this::generateMemberSwitchCases));
        writer.putContext("union", shape.isUnionShape());
        writer.putContext("illegalArg", IllegalArgumentException.class);
        writer.putContext("isError", shape.hasTrait(ErrorTrait.class));
        writer.putContext("hasChecks",
                shape.isUnionShape() || shape.members().stream().anyMatch(CodegenUtils::isRequiredWithNoDefault));
        writer.write(template);
        writer.popState();
    }

    private void generateMemberSwitchCases(JavaWriter writer) {
        int idx = 0;
        for (var iter = CodegenUtils.getSortedMembers(shape).iterator(); iter.hasNext(); idx++) {
            var member = iter.next();
            boolean isTracked = CodegenUtils.isRequiredWithNoDefault(member);
            boolean requiresNullCheck = CodegenUtils.requiresSetterNullCheck(symbolProvider, member);
            writer.pushState();
            var memberName = symbolProvider.toMemberName(member);
            var builderMethodName = memberName;
            if (isTracked || requiresNullCheck) {
                builderMethodName = memberName + "NoCheck";
            }
            writer.putContext("memberName", memberName);
            writer.putContext("builderMethodName", builderMethodName);
            writer.write(
                    "case $L -> builder.${builderMethodName:L}($C);",
                    idx,
                    new DeserializerGenerator(writer, member, symbolProvider, model, service, "de", "member"));
            writer.popState();
        }
    }
}
