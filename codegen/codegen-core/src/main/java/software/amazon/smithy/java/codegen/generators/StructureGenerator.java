/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ShapeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.BuilderSetterSection;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.sections.GetterSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.error.ErrorFault;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.PresenceTracker;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NodeVisitor;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
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
import software.amazon.smithy.model.traits.ClientOptionalTrait;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.UnitTypeTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class StructureGenerator<
        T extends ShapeDirective<StructureShape, CodeGenerationContext, JavaCodegenSettings>>
        implements Consumer<T> {

    @Override
    public void accept(T directive) {
        if (directive.shape().hasTrait(UnitTypeTrait.class) || directive.symbol()
                .getProperty(SymbolProperties.EXTERNAL_TYPE)
                .isPresent()) {
            // Skip Unit structures or external types.
            return;
        }
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));

            // Compute interface mixin symbols for implements clause
            List<Symbol> interfaceMixinSymbols = new ArrayList<>();
            for (var mixinId : shape.getMixins()) {
                Shape mixinShape = directive.model().expectShape(mixinId);
                if (MixinTrait.isInterfaceMixin(mixinShape)) {
                    interfaceMixinSymbols.add(directive.symbolProvider().toSymbol(mixinShape));
                }
            }

            var template =
                    """
                            public final class ${shape:T} ${^isError}implements ${serializableStruct:T}${#mixinInterfaces}, ${value:T}${/mixinInterfaces}${/isError}${?isError}extends ${sdkException:T}${?hasMixinInterfaces} implements ${#mixinInterfaces}${value:T}${^key.last}, ${/key.last}${/mixinInterfaces}${/hasMixinInterfaces}${/isError} {

                                ${schemas:C|}

                                ${id:C|}

                                ${properties:C|}

                                ${constructor:C|}

                                ${getters:C|}

                                ${toString:C|}

                                ${equals:C|}

                                ${^isError}${hashCode:C|}${/isError}

                                ${serializer:C|}

                                ${getMemberValue:C|}

                                ${toBuilder:C|}

                                ${builder:C|}
                            }
                            """;
            writer.putContext("isError", shape.hasTrait(ErrorTrait.class));
            writer.putContext("hasMixinInterfaces", !interfaceMixinSymbols.isEmpty());
            writer.putContext("mixinInterfaces", interfaceMixinSymbols);
            writer.putContext("shape", directive.symbol());
            writer.putContext("serializableStruct", SerializableStruct.class);

            var sdkError = CodegenUtils.tryGetServiceProperty(directive, SymbolProperties.SERVICE_EXCEPTION);
            writer.putContext("sdkException", sdkError == null ? ModeledException.class : sdkError);

            writer.putContext("id", new IdStringGenerator(writer, shape));
            writer.putContext(
                    "schemas",
                    new SchemaFieldGenerator(
                            directive,
                            writer,
                            shape));
            writer.putContext(
                    "properties",
                    new PropertyGenerator(writer, shape, directive.symbolProvider(), directive.model()));
            writer.putContext(
                    "constructor",
                    new ConstructorGenerator(writer, shape, directive.symbolProvider(), directive.model()));
            // Collect member names defined in interface mixins for @Override annotations
            Set<String> interfaceMixinMemberNames = new LinkedHashSet<>();
            for (var mixinId : shape.getMixins()) {
                Shape mixinShape = directive.model().expectShape(mixinId);
                if (MixinTrait.isInterfaceMixin(mixinShape)) {
                    for (MemberShape m : mixinShape.members()) {
                        interfaceMixinMemberNames.add(m.getMemberName());
                    }
                }
            }
            writer.putContext(
                    "getters",
                    new GetterGenerator(
                            writer,
                            shape,
                            directive.symbolProvider(),
                            directive.model(),
                            interfaceMixinMemberNames));
            writer.putContext(
                    "equals",
                    new EqualsGenerator(writer, shape, directive.symbolProvider(), directive.model()));
            writer.putContext("hashCode", new HashCodeGenerator(writer, shape, directive.symbolProvider()));
            writer.putContext("toString", new ToStringGenerator(writer));
            writer.putContext(
                    "serializer",
                    new StructureSerializerGenerator(
                            directive,
                            writer,
                            shape,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service()));
            writer.putContext(
                    "builder",
                    new StructureBuilderGenerator(
                            writer,
                            shape,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service(),
                            interfaceMixinSymbols,
                            interfaceMixinMemberNames));
            writer.putContext("getMemberValue", new GetMemberValueGenerator(writer, directive.symbolProvider(), shape));
            writer.putContext("toBuilder", new ToBuilderGenerator(writer, shape, directive.symbolProvider()));
            writer.write(template);
            writer.popState();
        });
    }

    private record PropertyGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model)
            implements
            Runnable {
        @Override
        public void run() {
            for (var member : shape.members()) {
                // Error message members do not generate property
                var memberName = symbolProvider.toMemberName(member);
                if (shape.hasTrait(ErrorTrait.class) && memberName.equals("message")) {
                    continue;
                }
                writer.pushState();
                writer.putContext("isNullable", CodegenUtils.isNullableMember(model, member));

                writer.write(
                        "private final transient ${?isNullable}$1B${/isNullable}${^isNullable}$1N${/isNullable} $2L;",
                        symbolProvider.toSymbol(member),
                        memberName);
                writer.popState();
            }
        }
    }

    private record ConstructorGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model)
            implements Runnable {

        @Override
        public void run() {
            writer.openBlock(
                    "private ${shape:T}(Builder builder) {",
                    "}",
                    () -> {
                        if (shape.hasTrait(ErrorTrait.class)) {
                            var error = shape.expectTrait(ErrorTrait.class);
                            writer.putContext("errorFault", ErrorFault.class);
                            if (error.isClientError()) {
                                writer.putContext("errorFaultType", "CLIENT");
                            } else if (error.isServerError()) {
                                writer.putContext("errorFaultType", "SERVER");
                            } else {
                                // Here just in case we ever expand this trait.
                                writer.putContext("errorFaultType", "OTHER");
                            }
                            boolean isMessagePresent = shape.getAllMembers()
                                    .values()
                                    .stream()
                                    .map(symbolProvider::toMemberName)
                                    .anyMatch("message"::equals);
                            if (isMessagePresent) {
                                writer.write(
                                        "super($$SCHEMA, builder.message, builder.$$cause, ${errorFault:T}.${errorFaultType:L}, builder.$$captureStackTrace, builder.$$deserialized);");
                            } else {
                                writer.write(
                                        "super($$SCHEMA, null, builder.$$cause, ${errorFault:T}.${errorFaultType:L}, builder.$$captureStackTrace, builder.$$deserialized);");
                            }
                        }

                        for (var member : shape.members()) {
                            var memberName = symbolProvider.toMemberName(member);
                            // Special case for error builders. Message is passed to
                            // the super constructor. No initializer is created.
                            if (shape.hasTrait(ErrorTrait.class) && memberName.equals("message")) {
                                continue;
                            }
                            writer.pushState();
                            writer.putContext("memberName", memberName);
                            writer.putContext("nullable", CodegenUtils.isNullableMember(model, member));
                            var target = model.expectShape(member.getTarget());
                            // Wrap maps and lists with immutable collection
                            if (target.isMapShape() || target.isListShape()) {
                                var memberSymbol = symbolProvider.toSymbol(member);
                                writer.putContext(
                                        "wrapper",
                                        memberSymbol.expectProperty(SymbolProperties.COLLECTION_IMMUTABLE_WRAPPER));
                                writer.putContext("collections", Collections.class);
                                writer.write(
                                        "this.${memberName:L} = ${?nullable}builder.${memberName:L} == null ? null : ${/nullable}${collections:T}.${wrapper:L}(builder.${memberName:L});");
                            } else if (target.isBlobShape() && !CodegenUtils.isStreamingBlob(target)) {
                                writer.write(
                                        "this.${memberName:L} = ${?nullable}builder.${memberName:L} == null ? null : ${/nullable}builder.${memberName:L}.duplicate();");
                            } else {
                                writer.write("this.${memberName:L} = builder.${memberName:L};");
                            }
                            writer.popState();
                        }
                    });
        }
    }

    private static final class GetterGenerator extends ShapeVisitor.Default<Void> implements Runnable {
        private final JavaWriter writer;
        private final Shape shape;
        private final SymbolProvider symbolProvider;
        private final Model model;
        private final Set<String> interfaceMixinMemberNames;
        private MemberShape member;

        private GetterGenerator(
                JavaWriter writer,
                Shape shape,
                SymbolProvider symbolProvider,
                Model model,
                Set<String> interfaceMixinMemberNames
        ) {
            this.writer = writer;
            this.symbolProvider = symbolProvider;
            this.model = model;
            this.shape = shape;
            this.interfaceMixinMemberNames = interfaceMixinMemberNames;
        }

        @Override
        public void run() {
            for (var member : shape.members()) {
                // Error message members do not generate a getter
                if (shape.hasTrait(ErrorTrait.class) && symbolProvider.toMemberName(member).equals("message")) {
                    continue;
                }
                writer.pushState();
                var memberName = symbolProvider.toMemberName(member);
                writer.putContext("memberName", memberName);
                writer.putContext("member", symbolProvider.toSymbol(member));
                writer.putContext("isNullable", CodegenUtils.isNullableMember(model, member));
                writer.putContext("getterName", CodegenUtils.toGetterName(member, model));
                writer.putContext("hasOverride",
                        interfaceMixinMemberNames.contains(member.getMemberName()));
                this.member = member;
                member.accept(this);
                writer.popState();
            }
        }

        @Override
        protected Void getDefault(Shape shape) {
            // If the member is not required then prefer the boxed type
            writer.pushState(new GetterSection(member));

            writer.write(
                    """
                            ${?hasOverride}@Override
                            ${/hasOverride}public ${?isNullable}${member:B}${/isNullable}${^isNullable}${member:N}${/isNullable} ${getterName:L}() {
                                return ${memberName:L};
                            }
                            """);
            writer.popState();
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            writeCollectionGetter(shape);
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            writeCollectionGetter(shape);
            return null;
        }

        private void writeCollectionGetter(Shape shape) {
            writer.pushState(new GetterSection(member));
            var shapeSymbol = symbolProvider.toSymbol(shape);
            writer.putContext("empty", shapeSymbol.expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD));
            writer.putContext("collections", Collections.class);
            writer.write(
                    """
                            ${?hasOverride}@Override
                            ${/hasOverride}public ${member:T} ${getterName:L}() {${?isNullable}
                                if (${memberName:L} == null) {
                                    return ${collections:T}.${empty:L};
                                }${/isNullable}
                                return ${memberName:L};
                            }
                            """);

            // Write has-er to allow users to check if a given collection was set. Required collections must be set.
            writer.write(
                    """
                            ${?hasOverride}@Override
                            ${/hasOverride}public boolean has${memberName:U}() {
                                return ${?isNullable}${memberName:L} != null${/isNullable}${^isNullable}true${/isNullable};
                            }
                            """);
            writer.popState();
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
        }
    }

    private record EqualsGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model)
            implements Runnable {
        @Override
        public void run() {
            if (shape.hasTrait(ErrorTrait.class)) {
                // Errors do not generate equals method
                return;
            }
            writer.write(
                    """
                            @Override
                            public boolean equals($T other) {
                                if (other == this) {
                                    return true;
                                }
                                ${C|}
                            }
                            """,
                    Object.class,
                    writer.consumer(this::writeMemberEquals));
        }

        private void writeMemberEquals(JavaWriter writer) {
            // If there are no properties to compare, and they are the same non-null
            // type then classes should be considered equal, and we can simplify the return
            if (shape.members().isEmpty()) {
                writer.writeInlineWithNoFormatting("return other != null && getClass() == other.getClass();");
                return;
            }
            writer.write(
                    """
                            if (other == null || getClass() != other.getClass()) {
                                return false;
                            }
                            $1T that = ($1T) other;
                            return ${2C|};""",
                    symbolProvider.toSymbol(shape),
                    writer.consumer(this::writePropertyEqualityChecks));
        }

        private void writePropertyEqualityChecks(JavaWriter writer) {
            var iter = shape.members().iterator();
            while (iter.hasNext()) {
                var member = iter.next();
                var memberSymbol = symbolProvider.toSymbol(member);
                writer.pushState();
                writer.putContext("memberName", symbolProvider.toMemberName(member));
                // Use `==` instead of `equals` for unboxed primitives
                if (memberSymbol.expectProperty(SymbolProperties.IS_PRIMITIVE)
                        && !CodegenUtils.isNullableMember(model, member)) {
                    writer.writeInline("this.${memberName:L} == that.${memberName:L}");
                } else {
                    Class<?> comparator = CodegenUtils.isJavaArray(memberSymbol) ? Arrays.class : Objects.class;
                    writer.writeInline("$T.equals(this.${memberName:L}, that.${memberName:L})", comparator);
                }
                if (iter.hasNext()) {
                    writer.writeInlineWithNoFormatting(writer.getNewline() + "&& ");
                }
                writer.popState();
            }
        }
    }

    private record HashCodeGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) implements
            Runnable {

        @Override
        public void run() {
            if (shape.hasTrait(ErrorTrait.class)) {
                // Errors do not generate hashcode
                return;
            }

            writer.write(
                    """
                            @Override
                            public int hashCode() {
                                ${C|}
                            }
                            """,
                    writer.consumer(this::generate));
        }

        private void generate(JavaWriter writer) {
            List<String> arrayMemberNames = shape.members()
                    .stream()
                    .filter(member -> CodegenUtils.isJavaArray(symbolProvider.toSymbol(member)))
                    .map(symbolProvider::toMemberName)
                    .toList();
            List<String> objectMemberNames = shape.members()
                    .stream()
                    .map(symbolProvider::toMemberName)
                    .filter(name -> !arrayMemberNames.contains(name))
                    .toList();
            writer.pushState();
            writer.putContext("arr", arrayMemberNames);
            writer.putContext("obj", objectMemberNames);
            writer.putContext("objects", Objects.class);
            if (arrayMemberNames.isEmpty()) {
                writer.write("return ${objects:T}.hash(${#obj}${value:L}${^key.last}, ${/key.last}${/obj});");
            } else {
                writer.putContext("arrays", Arrays.class);
                writer.write(
                        """
                                int result = ${objects:T}.hash(${#obj}${value:L}${^key.last}, ${/key.last}${/obj});
                                result = 31 * result${#arr} + ${arrays:T}.hashCode(${value:L})${/arr};
                                return result;
                                """);
            }
            writer.popState();
        }
    }

    private record ToBuilderGenerator(
            JavaWriter writer,
            StructureShape shape,
            SymbolProvider symbolProvider) implements Runnable {
        @Override
        public void run() {
            var members = new LinkedHashSet<>(shape.members().size());
            for (var member : shape.members()) {
                members.add(symbolProvider.toMemberName(member));
            }
            var hasErrorMessage = shape.hasTrait(ErrorTrait.class) && members.remove("message");

            writer.pushState();
            writer.putContext("hasErrorMessage", hasErrorMessage);
            writer.putContext("members", members);
            writer.write(
                    """
                            /**
                             * Create a new builder containing all the current property values of this object.
                             *
                             * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
                             *
                             * @return a builder for {@link ${shape:T}}.
                             */
                            public Builder toBuilder() {
                                var builder = new Builder();
                                ${?hasErrorMessage}builder.message(getMessage());
                                ${/hasErrorMessage}${#members}builder.${value:L}(this.${value:L});
                                ${/members}return builder;
                            }
                            """);
            writer.popState();
        }
    }

    private static final class StructureBuilderGenerator extends BuilderGenerator {
        private final List<Symbol> interfaceMixinSymbols;
        private final Set<String> interfaceMixinMemberNames;

        StructureBuilderGenerator(
                JavaWriter writer,
                Shape shape,
                SymbolProvider symbolProvider,
                Model model,
                ServiceShape service,
                List<Symbol> interfaceMixinSymbols,
                Set<String> interfaceMixinMemberNames
        ) {
            super(writer, shape, symbolProvider, model, service);
            this.interfaceMixinSymbols = interfaceMixinSymbols;
            this.interfaceMixinMemberNames = interfaceMixinMemberNames;
        }

        @Override
        protected List<Symbol> mixinBuilderInterfaces() {
            return interfaceMixinSymbols;
        }

        // Required shapes marked with clientOptional should not be required to create the type. For these shapes,
        // tell the tracker they're always set. Validation can detect later that they're provided.
        @Override
        protected void generateConstructor(JavaWriter writer) {
            List<MemberShape> requiredClientOptional = new ArrayList<>();
            for (var member : shape.members()) {
                if (CodegenUtils.isRequiredWithNoDefault(member) && member.hasTrait(ClientOptionalTrait.class)) {
                    requiredClientOptional.add(member);
                }
            }
            if (requiredClientOptional.isEmpty()) {
                // Make the default empty constructor.
                super.generateConstructor(writer);
            } else {
                // Make the constructor set the tracker members.
                writer.openBlock("private Builder() {", "}", () -> {
                    writer.write("// Tell the tracker to assume clientOptional members are present.");
                    for (var member : requiredClientOptional) {
                        var memberName = symbolProvider.toMemberName(member);
                        var schemaName = CodegenUtils.toMemberSchemaName(memberName);
                        writer.write("tracker.setMember($L);", schemaName);
                    }
                });
            }
        }

        @Override
        protected void generateErrorCorrection(JavaWriter writer) {
            if (shape.members().stream().noneMatch(CodegenUtils::isRequiredWithNoDefault)) {
                // Do not generate error correction if there are no required members with no default.
                return;
            }

            writer.write(
                    """
                            @Override
                            public ${sdkShapeBuilder:T}<${shape:T}> errorCorrection() {
                                if (tracker.allSet()) {
                                    return this;
                                }
                                ${C|}
                                return this;
                            }
                            """,
                    writer.consumer(this::writeErrorCorrectionMembers));
        }

        private void writeErrorCorrectionMembers(JavaWriter writer) {
            var visitor = new ErrorCorrectionVisitor(writer, symbolProvider, model);
            for (var member : shape.members()) {
                var target = model.expectShape(member.getTarget());
                if (CodegenUtils.isRequiredWithNoDefault(member)) {
                    var memberName = symbolProvider.toMemberName(member);
                    var schemaName = CodegenUtils.toMemberSchemaName(memberName);
                    visitor.memberShape = member;
                    writer.openBlock("if (!tracker.checkMember($L)) {", "}", schemaName, () -> {
                        if (assumeShapeHasErrorCorrectedDefault(target, member, symbolProvider, model)) {
                            writer.write("tracker.setMember($1L);", schemaName);
                        } else {
                            writer.write("$L($C);", memberName, visitor);
                        }
                    });
                }
            }
        }

        private static boolean assumeShapeHasErrorCorrectedDefault(
                Shape target,
                MemberShape member,
                SymbolProvider symbolProvider,
                Model model
        ) {
            return target.isStructureShape() || target.isUnionShape()
                    || CodegenUtils.hasBuiltinDefault(
                            symbolProvider,
                            model,
                            member);
        }

        private static final class ErrorCorrectionVisitor extends ShapeVisitor.Default<Void> implements Runnable {
            private final JavaWriter writer;
            private final SymbolProvider symbolProvider;
            private final Model model;
            private MemberShape memberShape;

            private ErrorCorrectionVisitor(JavaWriter writer, SymbolProvider symbolProvider, Model model) {
                this.writer = writer;
                this.symbolProvider = symbolProvider;
                this.model = model;
            }

            @Override
            public void run() {
                memberShape.accept(this);
            }

            @Override
            protected Void getDefault(Shape shape) {
                throw new IllegalArgumentException("Could not generate error correction value for " + shape);
            }

            @Override
            public Void blobShape(BlobShape blobShape) {
                if (blobShape.hasTrait(StreamingTrait.class)) {
                    writer.writeInline("$T.ofEmpty()", DataStream.class);
                } else {
                    writer.writeInline("$T.allocate(0)", ByteBuffer.class);
                }
                return null;
            }

            @Override
            public Void listShape(ListShape listShape) {
                writer.writeInline(
                        "$T.$L",
                        Collections.class,
                        symbolProvider.toSymbol(listShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD));
                return null;
            }

            @Override
            public Void mapShape(MapShape mapShape) {
                writer.writeInline(
                        "$T.$L",
                        Collections.class,
                        symbolProvider.toSymbol(mapShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD));
                return null;
            }

            @Override
            public Void intEnumShape(IntEnumShape shape) {
                writer.writeInline("$T.unknown(0)", symbolProvider.toSymbol(shape));
                return null;
            }

            @Override
            public Void documentShape(DocumentShape documentShape) {
                writer.writeInline("null");
                return null;
            }

            @Override
            public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
                writer.writeInline("$T.ZERO", BigInteger.class);
                return null;
            }

            @Override
            public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
                writer.writeInline("$T.ZERO", BigDecimal.class);
                return null;
            }

            @Override
            public Void stringShape(StringShape stringShape) {
                writer.writeInline("\"\"");
                return null;
            }

            @Override
            public Void enumShape(EnumShape shape) {
                writer.writeInline("$T.unknown(\"\")", symbolProvider.toSymbol(shape));
                return null;
            }

            @Override
            public Void timestampShape(TimestampShape timestampShape) {
                writer.write("$T.EPOCH", Instant.class);
                return null;
            }

            @Override
            public Void memberShape(MemberShape memberShape) {
                return model.expectShape(memberShape.getTarget()).accept(this);
            }
        }

        @Override
        protected void generateProperties(JavaWriter writer) {
            writer.pushState();

            // Add any static default properties
            var defaultVisitor = new DefaultInitializerGenerator(writer, model, symbolProvider);
            for (var member : shape.members()) {
                if (member.hasNonNullDefault()
                        && symbolProvider.toSymbol(member).expectProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT)) {
                    defaultVisitor.member = member;
                    writer.write(
                            "private static final $T $L = $C;",
                            symbolProvider.toSymbol(member),
                            CodegenUtils.toDefaultValueName(symbolProvider.toMemberName(member)),
                            defaultVisitor);
                }
            }

            // Add presence tracker if any members are required by validation.
            if (shape.members().stream().anyMatch(CodegenUtils::isRequiredWithNoDefault)) {
                writer.putContext("tracker", PresenceTracker.class);
                writer.write("private final ${tracker:T} tracker = ${tracker:T}.of($$SCHEMA);");
            }

            // Add non-static builder properties
            for (var member : shape.members()) {
                var memberName = symbolProvider.toMemberName(member);
                writer.pushState();
                writer.putContext("nullable", CodegenUtils.isNullableMember(model, member));
                writer.putContext("default", member.hasNonNullDefault());
                writer.putContext(
                        "static",
                        symbolProvider.toSymbol(member).expectProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT));
                defaultVisitor.member = member;
                writer.write(
                        "private ${^nullable}$1T${/nullable}${?nullable}$1B${/nullable} $2L${?default} = ${?static}$3L${/static}${^static}$4C${/static}${/default};",
                        symbolProvider.toSymbol(member),
                        memberName,
                        CodegenUtils.toDefaultValueName(memberName),
                        defaultVisitor);
                writer.popState();
            }
            if (shape.hasTrait(ErrorTrait.class)) {
                writer.write("private $T $$cause;", Throwable.class);
                writer.write("private $T $$captureStackTrace;", Boolean.class);
                writer.write("private boolean $$deserialized;");
            }
            writer.popState();
        }

        @Override
        protected void generateSetters(JavaWriter writer) {
            writer.pushState();
            writer.putContext("objects", Objects.class);
            writer.putContext("throwable", Throwable.class);
            for (var member : shape.members()) {
                writer.pushState(new BuilderSetterSection(member));
                writer.putContext("memberName", symbolProvider.toMemberName(member));
                writer.putContext("memberSymbol", symbolProvider.toSymbol(member));
                writer.putContext("tracked", CodegenUtils.isRequiredWithNoDefault(member));
                writer.putContext("check", CodegenUtils.requiresSetterNullCheck(symbolProvider, member));
                writer.putContext("isNullable", CodegenUtils.isNullableMember(model, member));
                writer.putContext("schemaName", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(member)));
                writer.putContext("hasOverride",
                        interfaceMixinMemberNames.contains(member.getMemberName()));

                writer.write(
                        """
                                ${?hasOverride}@Override
                                ${/hasOverride}public Builder ${memberName:L}(${?isNullable}${memberSymbol:B}${/isNullable}${^isNullable}${memberSymbol:N}${/isNullable} ${memberName:L}) {
                                    this.${memberName:L} = ${?check}${objects:T}.requireNonNull(${/check}${memberName:L}${?check}, "${memberName:L} cannot be null")${/check};${?tracked}
                                    tracker.setMember(${schemaName:L});${/tracked}
                                    return this;
                                }
                                """);
                writer.popState();
            }
            if (shape.hasTrait(ErrorTrait.class)) {
                writer.write("""
                        public Builder withStackTrace() {
                            this.$$captureStackTrace = true;
                            return this;
                        }

                        public Builder withoutStackTrace() {
                            this.$$captureStackTrace = false;
                            return this;
                        }

                        public Builder withCause(${throwable:T} cause) {
                            this.$$cause = cause;
                            return this;
                        }
                        """);
            }
            writer.popState();
        }

        @Override
        protected void generateBuild(JavaWriter writer) {
            writer.pushState();
            writer.putContext(
                    "hasRequiredMembers",
                    shape.members().stream().anyMatch(CodegenUtils::isRequiredWithNoDefault));
            writer.write("""
                    @Override
                    public ${shape:N} build() {${?hasRequiredMembers}
                        tracker.validate();${/hasRequiredMembers}
                        return new ${shape:T}(this);
                    }
                    """);
            writer.popState();
        }

        /**
         * Adds default values to builder properties.
         */
        private static final class DefaultInitializerGenerator extends ShapeVisitor.DataShapeVisitor<Void> implements
                Runnable {
            private final JavaWriter writer;
            private final Model model;
            private final SymbolProvider symbolProvider;
            private MemberShape member;
            private Node defaultValue;

            DefaultInitializerGenerator(
                    JavaWriter writer,
                    Model model,
                    SymbolProvider symbolProvider
            ) {
                this.writer = writer;
                this.model = model;
                this.symbolProvider = symbolProvider;
            }

            @Override
            public void run() {
                if (member.hasNonNullDefault()) {
                    this.defaultValue = member.expectTrait(DefaultTrait.class).toNode();
                    member.accept(this);
                }
            }

            @Override
            public Void blobShape(BlobShape blobShape) {
                var strValue = defaultValue.expectStringNode().getValue();
                writer.pushState();
                writer.putContext("dataStream", DataStream.class);
                writer.putContext("b64", Base64.class);
                writer.putContext("byteBuf", ByteBuffer.class);
                if (blobShape.hasTrait(StreamingTrait.class)) {
                    if (strValue.isEmpty()) {
                        writer.write("${dataStream:T}.ofEmpty()");
                    } else {
                        writer.write(
                                "${dataStream:T}.ofBytes(${b64:T}.getDecoder().decode($S))",
                                strValue);
                    }
                } else {
                    if (strValue.isEmpty()) {
                        writer.write("${byteBuf:T}.allocate(0);");
                    } else {
                        writer.write(
                                "${byteBuf:T}.wrap(${b64:T}.getDecoder().decode($S)).duplicate()",
                                strValue);
                    }
                }
                writer.popState();
                return null;
            }

            @Override
            public Void booleanShape(BooleanShape booleanShape) {
                writer.write("$L", defaultValue.expectBooleanNode().getValue());
                return null;
            }

            @Override
            public Void listShape(ListShape listShape) {
                // Note that Lists can _ONLY_ have empty maps as the default,
                // so we do not need to check the default value. See:
                // https://github.com/smithy-lang/smithy/blob/main/designs/defaults-and-model-evolution.md
                writer.write(
                        "$T.$L",
                        Collections.class,
                        symbolProvider.toSymbol(listShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD));
                return null;
            }

            @Override
            public Void mapShape(MapShape mapShape) {
                // Note that Maps can _ONLY_ have empty maps as the default,
                // so we do not need to check the default value. See:
                // https://github.com/smithy-lang/smithy/blob/main/designs/defaults-and-model-evolution.md
                writer.write(
                        "$T.$L",
                        Collections.class,
                        symbolProvider.toSymbol(mapShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD));
                return null;
            }

            @Override
            public Void byteShape(ByteShape byteShape) {
                // Bytes duplicate the integer toString method
                writer.write("$L", defaultValue.expectNumberNode().getValue().intValue());
                return null;
            }

            @Override
            public Void shortShape(ShortShape shortShape) {
                // Shorts duplicate the int toString method
                writer.write("$L", defaultValue.expectNumberNode().getValue().intValue());
                return null;
            }

            @Override
            public Void integerShape(IntegerShape integerShape) {
                writer.write("$L", defaultValue.expectNumberNode().getValue().intValue());
                return null;
            }

            @Override
            public Void intEnumShape(IntEnumShape shape) {
                var value = defaultValue.expectNumberNode().getValue().intValue();
                for (var entry : shape.getEnumValues().entrySet()) {
                    if (entry.getValue() == value) {
                        writer.write(
                                "$T.$L",
                                symbolProvider.toSymbol(member),
                                CodegenUtils.toUpperSnakeCase(entry.getKey()));
                        break;
                    }
                }
                return null;
            }

            @Override
            public Void longShape(LongShape longShape) {
                writer.write("$LL", defaultValue.expectNumberNode().getValue().longValue());
                return null;
            }

            @Override
            public Void floatShape(FloatShape floatShape) {
                writer.write("$Lf", defaultValue.expectNumberNode().getValue().floatValue());
                return null;
            }

            @Override
            public Void documentShape(DocumentShape documentShape) {
                writer.pushState();
                writer.putContext("document", Document.class);
                defaultValue.accept(new DocumentDefaultNodeVisitor(writer));
                writer.popState();
                return null;
            }

            @Override
            public Void doubleShape(DoubleShape doubleShape) {
                writer.write("$L", defaultValue.expectNumberNode().getValue().doubleValue());
                return null;
            }

            @Override
            public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
                writeBigNumberDefault(defaultValue.expectNumberNode().getValue(), BigInteger.class);
                return null;
            }

            @Override
            public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
                writeBigNumberDefault(defaultValue.expectNumberNode().getValue(), BigDecimal.class);
                return null;
            }

            private void writeBigNumberDefault(Number number, Class<?> clazz) {
                switch (number) {
                    case Long l -> writer.write("$T.valueOf($LL)", clazz, l);
                    case Double d -> writer.write("$T.valueOf($L)", clazz, d);
                    case BigInteger b -> writer.write("new $T($S)", clazz, b.toString());
                    case BigDecimal bd -> writer.write("new $T($S)", clazz, bd.toString());
                    default -> throw new IllegalStateException("Unexcepted value: " + number);
                }
            }

            @Override
            public Void stringShape(StringShape stringShape) {
                writer.write("$S", defaultValue.expectStringNode().getValue());
                return null;
            }

            @Override
            public Void enumShape(EnumShape shape) {
                var value = defaultValue.expectStringNode().getValue();
                for (var entry : shape.getEnumValues().entrySet()) {
                    if (entry.getValue().equals(value)) {
                        writer.write(
                                "$T.$L",
                                symbolProvider.toSymbol(member),
                                CodegenUtils.toUpperSnakeCase(entry.getKey()));
                        break;
                    }
                }
                return null;
            }

            @Override
            public Void structureShape(StructureShape structureShape) {
                throw new UnsupportedOperationException("Structure shape defaults cannot be set.");
            }

            @Override
            public Void unionShape(UnionShape unionShape) {
                throw new UnsupportedOperationException("Union shape defaults cannot be set.");

            }

            @Override
            public Void memberShape(MemberShape memberShape) {
                return model.expectShape(memberShape.getTarget()).accept(this);
            }

            @Override
            public Void timestampShape(TimestampShape timestampShape) {
                Instant value;
                if (member.hasTrait(TimestampFormatTrait.class)) {
                    value = switch (member.expectTrait(TimestampFormatTrait.class).getFormat()) {
                        case EPOCH_SECONDS -> Instant.ofEpochMilli(
                                defaultValue.expectNumberNode().getValue().longValue());
                        case HTTP_DATE -> Instant.from(
                                DateTimeFormatter.RFC_1123_DATE_TIME.parse(defaultValue.expectStringNode().getValue()));
                        default -> instantFromDefaultTimestamp(defaultValue);
                    };
                } else {
                    value = instantFromDefaultTimestamp(defaultValue);
                }
                writer.write("$T.ofEpochMilli($LL)", Instant.class, value.toEpochMilli());
                return null;
            }

            private static Instant instantFromDefaultTimestamp(Node defaultValue) {
                if (defaultValue.isNumberNode()) {
                    return Instant.ofEpochSecond(defaultValue.expectNumberNode().getValue().longValue());
                } else if (defaultValue.isStringNode()) {
                    return Instant.parse(defaultValue.expectStringNode().getValue());
                }
                throw new IllegalArgumentException(
                        "Invalid timestamp value node: " + defaultValue + "Expected string or number");
            }
        }

        private record DocumentDefaultNodeVisitor(JavaWriter writer) implements NodeVisitor<Void> {

            @Override
            public Void arrayNode(ArrayNode node) {
                // List defaults must always be empty
                writer.write("${document:T}.of($T.emptyList())", Collections.class);
                return null;
            }

            @Override
            public Void booleanNode(BooleanNode node) {
                writer.write("${document:T}.of($L)", node.getValue());
                return null;
            }

            @Override
            public Void nullNode(NullNode node) {
                throw new UnsupportedOperationException("Null default should not generate default value.");
            }

            @Override
            public Void numberNode(NumberNode node) {
                if (node.isFloatingPointNumber()) {
                    writer.write("${document:T}.of($L)", node.getValue().doubleValue());
                } else {
                    writer.write("${document:T}.of($L)", node.getValue().intValue());
                }
                return null;
            }

            @Override
            public Void objectNode(ObjectNode node) {
                // Map defaults must always be empty
                writer.write("${document:T}.of($T.emptyMap())", Collections.class);
                return null;
            }

            @Override
            public Void stringNode(StringNode node) {
                writer.write("${document:T}.of($S)", node.getValue());
                return null;
            }
        }
    }

}
