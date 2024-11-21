/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.ErrorTrait;

record GetMemberValueGenerator(JavaWriter writer, SymbolProvider symbolProvider, Shape shape) implements Runnable {
    @Override
    public void run() {
        writer.pushState();

        String template;
        if (shape.members().isEmpty()) {
            template = """
                @Override
                public <T> T getMemberValue(${sdkSchema:N} member) {
                    throw new ${iae:T}("Attempted to get non-existent member: " + member.id());
                }
                """;
        } else {
            template = """
                @Override
                public <T> T getMemberValue(${sdkSchema:N} member) {
                    return switch (member.memberIndex()) {
                        ${cases:C|}
                        default -> throw new ${iae:T}("Attempted to get non-existent member: " + member.id());
                    };
                }
                """;
        }
        writer.putContext("sdkSchema", Schema.class);
        writer.putContext("cases", writer.consumer(this::generateMemberSwitchCases));
        writer.putContext("iae", IllegalArgumentException.class);
        writer.write(template);
        writer.popState();
    }

    private void generateMemberSwitchCases(JavaWriter writer) {
        int idx = 0;
        var isError = shape.hasTrait(ErrorTrait.class);
        for (var iter = CodegenUtils.getSortedMembers(shape).iterator(); iter.hasNext(); idx++) {
            var member = iter.next();
            writer.pushState();
            writer.putContext("schemaUtilsClass", SchemaUtils.class);
            writer.putContext("memberName", symbolProvider.toMemberName(member));
            writer.putContext("memberSchema", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(member)));
            if (shape.getType() == ShapeType.UNION) {
                // Unions need to access the member value using a getter, since subtypes provide the values.
                writer.write(
                    "case $L -> (T) ${schemaUtilsClass:T}.validateSameMember(${memberSchema:L}, member, ${memberName:L}());",
                    idx
                );
            } else if (isError && member.getMemberName().equalsIgnoreCase("message")) {
                // Exception message values have to use a special getter.
                writer.write(
                    "case $L -> (T) ${schemaUtilsClass:T}.validateSameMember(${memberSchema:L}, member, getMessage());",
                    idx
                );
            } else {
                // Other values can just skip the getter.
                writer.write(
                    "case $L -> (T) ${schemaUtilsClass:T}.validateSameMember(${memberSchema:L}, member, ${memberName:L});",
                    idx
                );
            }
            writer.popState();
        }
    }
}
