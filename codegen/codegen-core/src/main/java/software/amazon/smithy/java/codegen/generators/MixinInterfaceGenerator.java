/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ShapeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates a Java interface for a Smithy mixin shape with {@code @mixin(interface = true)}.
 *
 * <p>The generated interface contains getter method signatures for the mixin's own members
 * (excluding members inherited from parent interface mixins). If the mixin extends other
 * interface mixins, the generated interface will extend those parent interfaces.
 *
 * <p>For collection members (List/Map), a {@code hasX()} method signature is also generated.
 */
@SmithyInternalApi
public final class MixinInterfaceGenerator<
        T extends ShapeDirective<? extends Shape, CodeGenerationContext, JavaCodegenSettings>>
        implements Consumer<T> {

    @Override
    public void accept(T directive) {
        var shape = directive.shape();
        var model = directive.model();
        var symbolProvider = directive.symbolProvider();

        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));

            // Compute parent interface mixin symbols for extends clause
            List<Symbol> parentInterfaces = new ArrayList<>();
            for (ShapeId mixinId : shape.getMixins()) {
                Shape mixinShape = model.expectShape(mixinId);
                if (MixinTrait.isInterfaceMixin(mixinShape)) {
                    parentInterfaces.add(symbolProvider.toSymbol(mixinShape));
                }
            }

            var template =
                    """
                            public interface ${shape:T}${?hasParents} extends ${#parents}${value:T}${^key.last}, ${/key.last}${/parents}${/hasParents} {
                                ${getters:C|}
                            }
                            """;
            writer.putContext("shape", directive.symbol());
            writer.putContext("hasParents", !parentInterfaces.isEmpty());
            writer.putContext("parents", parentInterfaces);
            writer.putContext("getters",
                    new GetterSignatureGenerator(writer, shape, symbolProvider, model, parentInterfaces));
            writer.write(template);

            writer.popState();
        });
    }

    private record GetterSignatureGenerator(
            JavaWriter writer,
            Shape shape,
            SymbolProvider symbolProvider,
            Model model,
            List<Symbol> parentInterfaces) implements Runnable {
        @Override
        public void run() {
            for (MemberShape member : shape.members()) {
                if (isMemberFromParentInterface(member)) {
                    continue;
                }
                writer.pushState();
                var target = model.expectShape(member.getTarget());
                writer.putContext("member", symbolProvider.toSymbol(member));
                writer.putContext("getterName", CodegenUtils.toGetterName(member, model));
                writer.putContext("isNullable", CodegenUtils.isNullableMember(model, member));

                if (target.isListShape() || target.isMapShape()) {
                    var memberName = symbolProvider.toMemberName(member);
                    writer.putContext("memberName", memberName);
                    writer.write(
                            """
                                    ${member:T} ${getterName:L}();

                                    boolean has${memberName:U}();
                                    """);
                } else {
                    writer.write(
                            "${?isNullable}${member:B}${/isNullable}${^isNullable}${member:N}${/isNullable} ${getterName:L}();");
                    writer.write("");
                }
                writer.popState();
            }
        }

        private boolean isMemberFromParentInterface(MemberShape member) {
            for (ShapeId mixinId : shape.getMixins()) {
                StructureShape mixinShape = model.expectShape(mixinId, StructureShape.class);
                if (MixinTrait.isInterfaceMixin(mixinShape)
                        && mixinShape.getAllMembers().containsKey(member.getMemberName())) {
                    return true;
                }
            }
            return false;
        }
    }
}
