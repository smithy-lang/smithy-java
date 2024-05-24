/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Objects;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *  Generates Builder setter methods for a structure shape
 */
record StructureBuilderSetterGenerator(
    JavaWriter writer, SymbolProvider symbolProvider, Model model, StructureShape shape
) implements Runnable {

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("objects", Objects.class);
        for (var member : shape.members()) {
            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(member));
            writer.putContext("memberSymbol", symbolProvider.toSymbol(member));
            writer.putContext("tracked", CodegenUtils.isRequiredWithNoDefault(member));
            writer.putContext("check", CodegenUtils.requiresSetterNullCheck(symbolProvider, member));
            writer.putContext("schemaName", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(member)));

            // If streaming blob then a setter must be added to allow
            // operation to set on builder.
            if (CodegenUtils.isStreamingBlob(model.expectShape(member.getTarget()))) {
                writer.write("""
                    @Override
                    public void setDataStream($T stream) {
                        ${memberName:L}(stream);
                    }
                    """, DataStream.class);
            }
            writer.write(
                """
                    public Builder ${memberName:L}(${memberSymbol:T} ${memberName:L}) {
                        this.${memberName:L} = ${?check}${objects:T}.requireNonNull(${/check}${memberName:L}${?check}, "${memberName:L} cannot be null")${/check};${?tracked}
                        tracker.setMember(${schemaName:L});${/tracked}
                        return this;
                    }
                    """
            );
            writer.popState();
        }
        writer.popState();
    }
}
