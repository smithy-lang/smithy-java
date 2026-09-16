/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.emitDefaultConstructor;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.emitThrow;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.findBuild;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.invoke;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecPlan;
import software.amazon.smithy.java.codecs.commons.internal.codegen.UnsupportedSchemaException;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassWriter;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Label;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.MethodVisitor;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Type;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Emits XML writers for a shape closure.
 *
 * <p>The interpreted serializer discovers element names, namespaces, and attribute-ness per value by
 * walking schema extension tables, and it defers the {@code '>'} of an opening tag because it does not
 * know until it has entered a structure whether attributes follow. All of that is decided here, once,
 * at generation time: every tag becomes a baked byte run, attributes become straight-line calls, and
 * the deferred {@code '>'} disappears because the generator knows which case it is in.
 *
 * <p>Byte-identity with the interpreted path is the hard constraint, so names are not recomputed from
 * traits by this class where the interpreted path uses something else. Structure member names come from
 * the same {@link XmlSchemaExtensions.StructExtension} tables, and list and map framing comes from the
 * same {@link XmlInfo} the dispatch serializer consults. Where those two sources disagree with each
 * other, this class reproduces the disagreement rather than resolving it.
 */
final class XmlRuntimeCodegenBackend implements RuntimeCodecBackend<GeneratedXmlCodec>, Opcodes {
    private static final String CODEC = Type.getInternalName(GeneratedXmlCodec.class);
    private static final String WRITER = Type.getInternalName(XmlCodegenWriter.class);
    private static final String READER = Type.getInternalName(SmithyXmlDeserializer.class);
    private static final String SHAPE_BUILDER = Type.getInternalName(ShapeBuilder.class);
    private static final String SERIALIZABLE_SHAPE = Type.getInternalName(SerializableShape.class);
    private static final String SMITHY_ENUM = Type.getInternalName(SmithyEnum.class);
    private static final String SMITHY_INT_ENUM = Type.getInternalName(SmithyIntEnum.class);
    private static final String BYTE_BUFFER = Type.getInternalName(ByteBuffer.class);
    private static final String INSTANT = Type.getInternalName(Instant.class);
    private static final String SERIALIZATION_EXCEPTION = Type.getInternalName(SerializationException.class);

    /**
     * Where a structure reader's flattened accumulators start.
     *
     * <p>Locals 0 through 2 are {@code this}, the reader, and the builder; 3 and 4 hold the packed
     * container token, which is a long.
     */
    private static final int FIRST_ACCUMULATOR_LOCAL = 5;

    /** Matches the writer's chunking, and {@link Budgets#maxMembersPerReaderBucket}. */
    private static final int MAX_ATTRIBUTES_PER_METHOD = 8;

    private static final Map<Class<?>, Class<?>> BOXES = Map.of(
            boolean.class,
            Boolean.class,
            byte.class,
            Byte.class,
            short.class,
            Short.class,
            int.class,
            Integer.class,
            long.class,
            Long.class,
            float.class,
            Float.class,
            double.class,
            Double.class);

    private final XmlInfo xmlInfo;

    XmlRuntimeCodegenBackend(XmlInfo xmlInfo) {
        this.xmlInfo = xmlInfo;
    }

    @Override
    public String id() {
        return "xml";
    }

    @Override
    public Class<GeneratedXmlCodec> codecType() {
        return GeneratedXmlCodec.class;
    }

    @Override
    public Class<?> lookupHost() {
        return GeneratedXmlCodec.class;
    }

    @Override
    public Mode mode() {
        return Mode.READ_WRITE;
    }

    @Override
    public Budgets budgets() {
        return new Budgets(220, 300, 8, 8);
    }

    @Override
    public Emission emit(RuntimeCodecPlan plan, String generatedName) {
        validate(plan);
        return new Generator(plan, generatedName, xmlInfo).generate();
    }

    private static void validate(RuntimeCodecPlan plan) {
        for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
            rejectModeledException(structure);
            var extension = structExtension(structure.schema());
            // Readers name the builder in their descriptors and build nested shapes through the
            // factory, so both have to be resolvable before a single method is emitted.
            if (structure.builderClass() == null || structure.builderFactory() == null) {
                throw new UnsupportedSchemaException("No builder for " + structure.schema().id());
            }
            if (structure.union()) {
                try {
                    structure.builderClass().getMethod("$unknownMember", String.class);
                } catch (NoSuchMethodException e) {
                    // The interpreted path records an unrecognized variant instead of dropping it, and
                    // a generated reader that silently dropped it would read a different value.
                    throw new UnsupportedSchemaException(
                            "Union builder cannot record an unknown variant: " + structure.schema().id());
                }
            }
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                if (member.setter() == null) {
                    throw new UnsupportedSchemaException("No builder setter for " + member.schema().id());
                }
                if (structure.union()) {
                    if (member.unionVariant() == null || member.unionAccessor() == null) {
                        throw new UnsupportedSchemaException(
                                "Unresolved union variant for " + member.schema().id());
                    }
                    if (extension != null && isAttribute(extension, member.schema())) {
                        throw new UnsupportedSchemaException(
                                "Attribute union members are not lowered: " + member.schema().id());
                    }
                } else if (member.getter() == null) {
                    throw new UnsupportedSchemaException("No getter for " + member.schema().id());
                }
                ShapeType type = member.target().type();
                if (extension != null && isAttribute(extension, member.schema())) {
                    // The interpreted path routes attributes through InlineAttributeSerializer, which
                    // only implements this set and throws for the rest. Falling back keeps the failure
                    // identical instead of inventing a lowering the dispatch path does not have.
                    switch (type) {
                        case STRING, ENUM, BOOLEAN, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE, TIMESTAMP -> {
                        }
                        default -> throw new UnsupportedSchemaException(
                                "XML runtime codegen cannot write " + type + " as an attribute at "
                                        + member.schema().id());
                    }
                    continue;
                }
                switch (type) {
                    case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL,
                            STRING, ENUM, INT_ENUM, BLOB, TIMESTAMP, LIST, MAP, STRUCTURE, UNION ->
                        {
                        }
                    // DOCUMENT is deliberately absent: the interpreted serializer writes an empty
                    // element for it, which looks more like a gap than a contract. Falling back keeps
                    // that behaviour in one place instead of baking it into generated code too.
                    default -> throw new UnsupportedSchemaException(
                            "XML runtime codegen does not yet lower " + type + " at " + member.schema().id());
                }
            }
        }
    }

    /**
     * Refuses to generate serde for modeled exceptions.
     *
     * <p>Generated readers populate a builder through its setters and never call
     * {@link ShapeBuilder#deserialize}, which is the only place generated model code sets the flag
     * behind {@code ModeledException.deserialized()}. Falling back preserves that lifecycle state.
     */
    private static void rejectModeledException(RuntimeCodecPlan.StructPlan structure) {
        Class<?> shapeClass = structure.shapeClass();
        if (shapeClass != null && ModeledException.class.isAssignableFrom(shapeClass)) {
            throw new UnsupportedSchemaException(
                    "XML runtime codegen does not support modeled exceptions: " + structure.schema().id());
        }
    }

    private static XmlSchemaExtensions.StructExtension structExtension(Schema schema) {
        Schema target = schema.isMember() ? schema.memberTarget() : schema;
        var extension = target.getExtension(XmlSchemaExtensions.KEY);
        return extension instanceof XmlSchemaExtensions.StructExtension se ? se : null;
    }

    private static boolean isAttribute(XmlSchemaExtensions.StructExtension extension, Schema member) {
        boolean[] table = extension.isAttributeTable();
        int index = member.memberIndex();
        return index >= 0 && index < table.length && table[index];
    }

    private static boolean isFlattened(XmlSchemaExtensions.StructExtension extension, Schema member) {
        boolean[] table = extension.isFlattenedTable();
        int index = member.memberIndex();
        return index >= 0 && index < table.length && table[index];
    }

    private static final class Generator {
        private final RuntimeCodecPlan plan;
        private final String className;
        private final XmlInfo xmlInfo;
        private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        private final Map<ShapeId, Integer> structureIds = new LinkedHashMap<>();
        private final Map<ShapeId, RuntimeCodecPlan.StructPlan> structuresById = new LinkedHashMap<>();

        /** Tag runs, deduplicated by content: {@code </member>} is written from one field everywhere. */
        private final Map<String, Integer> constants = new LinkedHashMap<>();

        private final Map<ListKey, Integer> listIds = new LinkedHashMap<>();
        private final List<ListAggregate> lists = new ArrayList<>();
        private final Map<MapKey, Integer> mapIds = new LinkedHashMap<>();
        private final List<MapAggregate> maps = new ArrayList<>();

        /** One string-to-constant mapper per enum shape, shared by every member that reads it. */
        private final Map<Class<?>, Integer> enumIds = new LinkedHashMap<>();
        private final Map<Class<?>, Integer> intEnumIds = new LinkedHashMap<>();

        private int methodCount;

        Generator(RuntimeCodecPlan plan, String className, XmlInfo xmlInfo) {
            this.plan = plan;
            this.className = className;
            this.xmlInfo = xmlInfo;
            for (int i = 0; i < plan.structures().size(); i++) {
                RuntimeCodecPlan.StructPlan structure = plan.structures().get(i);
                structureIds.put(structure.schema().id(), i);
                structuresById.put(structure.schema().id(), structure);
            }
        }

        Emission generate() {
            writer.visit(V17, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Object", new String[] {CODEC});
            emitConstructor();

            for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
                emitWriter(structure);
            }
            for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
                emitReader(structure);
            }
            // Aggregates are discovered by both sides, so this has to run after every structure has
            // been emitted in both directions or a list found only by a reader would go unemitted.
            emitAggregates();
            // Enum mappers are registered while readers are emitted, aggregates included, and register
            // nothing themselves.
            emitEnumReaders();
            emitIntEnumReaders();
            emitWriteEntry(plan.rootStructure());
            emitReadEntry(plan.rootStructure());

            // Fields and <clinit> come last because every tag run above registered itself on the way
            // through. The classfile encoder buffers members, so declaration order here does not matter.
            emitFields();
            emitClassInitializer();
            writer.visitEnd();
            return new Emission(writer.toByteArray(), methodCount);
        }

        private void emitConstructor() {
            emitDefaultConstructor(writer);
            methodCount++;
        }

        private void emitFields() {
            for (int id : constants.values()) {
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "T" + id, "[B", null, null).visitEnd();
            }
        }

        private void emitClassInitializer() {
            MethodVisitor method = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            method.visitCode();
            for (Map.Entry<String, Integer> entry : constants.entrySet()) {
                method.visitLdcInsn(entry.getKey());
                method.visitFieldInsn(
                        GETSTATIC,
                        Type.getInternalName(StandardCharsets.class),
                        "UTF_8",
                        "Ljava/nio/charset/Charset;");
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "java/lang/String",
                        "getBytes",
                        "(Ljava/nio/charset/Charset;)[B",
                        false);
                method.visitFieldInsn(PUTSTATIC, className, "T" + entry.getValue(), "[B");
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        /**
         * The root element's tags arrive as arguments, so this method is the one place the generator
         * does not bake a name. See {@link GeneratedXmlCodec#write} for why.
         *
         * <p>The {@code '>'} that {@code open} stops short of is written here when the root has no
         * attributes; when it does, the structure writer emits it after them, exactly as the deferred
         * {@code pendingClose} of the dispatch serializer would.
         */
        private void emitWriteEntry(RuntimeCodecPlan.StructPlan root) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "write",
                    "(L" + SERIALIZABLE_SHAPE + ";L" + WRITER + ";[B[B)V",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "raw", "([B)V", false);
            if (!hasAttributes(root)) {
                emitRaw(method, 2, constant(">"));
            }
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitTypeInsn(CHECKCAST, Type.getInternalName(root.shapeClass()));
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    writerName(root),
                    writerDescriptor(root),
                    false);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "raw", "([B)V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        // ------------------------------------------------------------------------------------------
        // Structures
        // ------------------------------------------------------------------------------------------

        /**
         * Emits the body of one structure: attributes, then the {@code '>'} they were waiting on, then
         * elements. The caller owns the opening and closing tags, because only the caller knows the
         * element name this structure appears under.
         */
        private void emitWriter(RuntimeCodecPlan.StructPlan structure) {
            if (structure.union()) {
                emitUnionWriter(structure);
                return;
            }
            var extension = structExtension(structure.schema());
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    writerName(structure),
                    writerDescriptor(structure),
                    null,
                    null);
            method.visitCode();

            if (extension != null) {
                for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                    if (isAttribute(extension, member.schema())) {
                        emitWriteAttribute(method, extension, member);
                    }
                }
                if (hasAttributes(structure)) {
                    emitRaw(method, 2, constant(">"));
                }
            }

            List<RuntimeCodecPlan.MethodRange> chunks = structure.writerChunks();
            for (int chunk = 0; chunk < chunks.size(); chunk++) {
                if (!hasElements(structure, extension, chunks.get(chunk))) {
                    continue;
                }
                method.visitVarInsn(ALOAD, 0);
                method.visitVarInsn(ALOAD, 1);
                method.visitVarInsn(ALOAD, 2);
                method.visitMethodInsn(
                        INVOKESPECIAL,
                        className,
                        writerChunkName(structure, chunk),
                        writerDescriptor(structure),
                        false);
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;

            for (int chunk = 0; chunk < chunks.size(); chunk++) {
                RuntimeCodecPlan.MethodRange range = chunks.get(chunk);
                if (!hasElements(structure, extension, range)) {
                    continue;
                }
                MethodVisitor chunkMethod = writer.visitMethod(
                        ACC_PRIVATE,
                        writerChunkName(structure, chunk),
                        writerDescriptor(structure),
                        null,
                        null);
                chunkMethod.visitCode();
                for (int i = range.startInclusive(); i < range.endExclusive(); i++) {
                    RuntimeCodecPlan.MemberPlan member = structure.members().get(i);
                    if (extension == null || !isAttribute(extension, member.schema())) {
                        emitWriteElement(chunkMethod, extension, member);
                    }
                }
                chunkMethod.visitInsn(RETURN);
                chunkMethod.visitMaxs(0, 0);
                chunkMethod.visitEnd();
                methodCount++;
            }
        }

        /**
         * A union is a structure with exactly one member set, so it frames identically: no discriminator
         * and no wrapper beyond the element the union itself sits in. Which member is set is a type test
         * on the variant class rather than a null check on a getter.
         */
        private void emitUnionWriter(RuntimeCodecPlan.StructPlan structure) {
            var extension = structExtension(structure.schema());
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    writerName(structure),
                    writerDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                Label next = new Label();
                method.visitVarInsn(ALOAD, 1);
                method.visitTypeInsn(INSTANCEOF, Type.getInternalName(member.unionVariant()));
                method.visitJumpInsn(IFEQ, next);
                Method accessor = member.unionAccessor();
                Class<?> valueType = accessor.getReturnType();
                Tags tags = memberTags(extension, member.schema());
                if (valueType.isPrimitive()) {
                    method.visitVarInsn(ALOAD, 2);
                    pushConstant(method, constant(tags.open() + ">"));
                    method.visitVarInsn(ALOAD, 1);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(member.unionVariant()));
                    invoke(method, accessor);
                    emitElementOnStack(method, member.schema(), valueType, constant(tags.close()));
                } else {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(member.unionVariant()));
                    invoke(method, accessor);
                    method.visitVarInsn(ASTORE, 3);
                    emitSlot(method, member.schema(), 3, tags, isFlattenedMember(extension, member.schema()));
                }
                method.visitInsn(RETURN);
                method.visitLabel(next);
            }
            emitThrow(method, "Unsupported or unknown union variant for " + structure.schema().id());
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWriteAttribute(
                MethodVisitor method,
                XmlSchemaExtensions.StructExtension extension,
                RuntimeCodecPlan.MemberPlan member
        ) {
            // Attributes carry no namespace run: the interpreted InlineAttributeSerializer writes only
            // the resolved member name.
            int prefixId = constant(" " + memberName(extension, member.schema()) + "=\"");
            Label skip = new Label();
            emitPresenceGuard(method, member, skip);
            Class<?> returnType = member.getter().getReturnType();
            if (returnType.isPrimitive()) {
                method.visitVarInsn(ALOAD, 2);
                pushConstant(method, prefixId);
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.getter());
                emitAttributeOnStack(method, member.schema(), returnType);
            } else {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.getter());
                method.visitVarInsn(ASTORE, 3);
                method.visitVarInsn(ALOAD, 3);
                method.visitJumpInsn(IFNULL, skip);
                method.visitVarInsn(ALOAD, 2);
                pushConstant(method, prefixId);
                method.visitVarInsn(ALOAD, 3);
                emitAttributeOnStack(method, member.schema(), Object.class);
            }
            method.visitLabel(skip);
        }

        private void emitWriteElement(
                MethodVisitor method,
                XmlSchemaExtensions.StructExtension extension,
                RuntimeCodecPlan.MemberPlan member
        ) {
            Label skip = new Label();
            emitPresenceGuard(method, member, skip);
            Tags tags = memberTags(extension, member.schema());
            Class<?> returnType = member.getter().getReturnType();
            if (returnType.isPrimitive()) {
                // A primitive getter cannot be null, and a primitive target is always a scalar.
                method.visitVarInsn(ALOAD, 2);
                pushConstant(method, constant(tags.open() + ">"));
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.getter());
                emitElementOnStack(method, member.schema(), returnType, constant(tags.close()));
            } else {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.getter());
                method.visitVarInsn(ASTORE, 3);
                method.visitVarInsn(ALOAD, 3);
                method.visitJumpInsn(IFNULL, skip);
                emitSlot(method, member.schema(), 3, tags, isFlattenedMember(extension, member.schema()));
            }
            method.visitLabel(skip);
        }

        private void emitPresenceGuard(MethodVisitor method, RuntimeCodecPlan.MemberPlan member, Label skip) {
            Method presence = member.presence();
            if (presence != null) {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, presence);
                method.visitJumpInsn(IFEQ, skip);
            }
        }

        /**
         * Writes one non-null value into the element named by {@code tags}.
         *
         * <p>The opening tag gets its {@code '>'} here unless the value is a structure or union that has
         * attributes, in which case the structure's own writer emits the {@code '>'} after them. A
         * flattened aggregate writes no wrapper at all; its items carry the member's name instead.
         */
        private void emitSlot(
                MethodVisitor method,
                Schema slot,
                int valueLocal,
                Tags tags,
                boolean flattened
        ) {
            Schema target = slot.isMember() ? slot.memberTarget() : slot;
            switch (target.type()) {
                case STRUCTURE, UNION -> {
                    RuntimeCodecPlan.StructPlan nested = structuresById.get(target.id());
                    if (nested == null) {
                        throw new UnsupportedSchemaException("Unplanned structure " + target.id());
                    }
                    emitRaw(method, 2, constant(hasAttributes(nested) ? tags.open() : tags.open() + ">"));
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(nested.shapeClass()));
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            writerName(nested),
                            writerDescriptor(nested),
                            false);
                    emitRaw(method, 2, constant(tags.close()));
                }
                case LIST, SET -> {
                    int aggregate = listAggregate(slot);
                    if (!flattened) {
                        emitRaw(method, 2, constant(tags.open() + ">"));
                    }
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, "java/util/List");
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            "writeL" + aggregate,
                            "(Ljava/util/List;L" + WRITER + ";)V",
                            false);
                    if (!flattened) {
                        emitRaw(method, 2, constant(tags.close()));
                    }
                }
                case MAP -> {
                    int aggregate = mapAggregate(slot);
                    if (!flattened) {
                        emitRaw(method, 2, constant(tags.open() + ">"));
                    }
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, "java/util/Map");
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            "writeM" + aggregate,
                            "(Ljava/util/Map;L" + WRITER + ";)V",
                            false);
                    if (!flattened) {
                        emitRaw(method, 2, constant(tags.close()));
                    }
                }
                default -> {
                    if (flattened) {
                        // The interpreted path would write bare text with no element around it. That is
                        // almost certainly a modelling error rather than an intended wire shape, so fall
                        // back rather than bake it in.
                        throw new UnsupportedSchemaException(
                                "@xmlFlattened on non-aggregate member " + slot.id());
                    }
                    method.visitVarInsn(ALOAD, 2);
                    pushConstant(method, constant(tags.open() + ">"));
                    method.visitVarInsn(ALOAD, valueLocal);
                    emitElementOnStack(method, slot, Object.class, constant(tags.close()));
                }
            }
        }

        // ------------------------------------------------------------------------------------------
        // Aggregates
        // ------------------------------------------------------------------------------------------

        /**
         * Emits every list and map writer, including ones discovered while emitting another.
         *
         * <p>Both worklists grow during emission, and the loop drains them until neither does.
         * Registration is keyed by shape plus resolved framing, so a recursive aggregate reaches a key
         * it has already seen and the loop terminates.
         */
        private void emitAggregates() {
            int listsDone = 0;
            int mapsDone = 0;
            while (listsDone < lists.size() || mapsDone < maps.size()) {
                while (listsDone < lists.size()) {
                    ListAggregate aggregate = lists.get(listsDone++);
                    emitListWriter(aggregate);
                    emitListReader(aggregate);
                    emitListItemReader(aggregate);
                }
                while (mapsDone < maps.size()) {
                    MapAggregate aggregate = maps.get(mapsDone++);
                    emitMapWriter(aggregate);
                    emitMapReader(aggregate);
                    emitMapEntryReader(aggregate);
                }
            }
        }

        private void emitListWriter(ListAggregate aggregate) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "writeL" + aggregate.id(),
                    "(Ljava/util/List;L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            Schema item = aggregate.slot().listMember();
            Tags tags = aggregate.tags();
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ISTORE, 3);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
            method.visitVarInsn(ISTORE, 4);
            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ILOAD, 3);
            method.visitVarInsn(ILOAD, 4);
            method.visitJumpInsn(IF_ICMPGE, done);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ILOAD, 3);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
            method.visitVarInsn(ASTORE, 5);
            Label nonNull = new Label();
            Label next = new Label();
            method.visitVarInsn(ALOAD, 5);
            method.visitJumpInsn(IFNONNULL, nonNull);
            emitEmptyElement(method, tags);
            method.visitJumpInsn(GOTO, next);
            method.visitLabel(nonNull);
            emitSlot(method, item, 5, tags, false);
            method.visitLabel(next);
            method.visitIincInsn(3, 1);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitMapWriter(MapAggregate aggregate) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "writeM" + aggregate.id(),
                    "(Ljava/util/Map;L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            Schema value = aggregate.slot().mapValueMember();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "entrySet", "()Ljava/util/Set;", true);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Set", "iterator", "()Ljava/util/Iterator;", true);
            method.visitVarInsn(ASTORE, 3);
            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
            method.visitJumpInsn(IFEQ, done);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
            method.visitTypeInsn(CHECKCAST, "java/util/Map$Entry");
            method.visitVarInsn(ASTORE, 4);

            emitRaw(method, 2, constant("<" + aggregate.entryName() + ">"));

            method.visitVarInsn(ALOAD, 2);
            pushConstant(method, constant("<" + aggregate.keyName() + aggregate.keyNamespace() + ">"));
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map$Entry",
                    "getKey",
                    "()Ljava/lang/Object;",
                    true);
            method.visitTypeInsn(CHECKCAST, "java/lang/String");
            pushConstant(method, constant("</" + aggregate.keyName() + ">"));
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    WRITER,
                    "elementString",
                    "([BLjava/lang/String;[B)V",
                    false);

            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map$Entry",
                    "getValue",
                    "()Ljava/lang/Object;",
                    true);
            method.visitVarInsn(ASTORE, 5);
            Tags valueTags = aggregate.valueTags();
            Label nonNull = new Label();
            Label next = new Label();
            method.visitVarInsn(ALOAD, 5);
            method.visitJumpInsn(IFNONNULL, nonNull);
            emitEmptyElement(method, valueTags);
            method.visitJumpInsn(GOTO, next);
            method.visitLabel(nonNull);
            emitSlot(method, value, 5, valueTags, false);
            method.visitLabel(next);

            emitRaw(method, 2, constant("</" + aggregate.entryName() + ">"));
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private int listAggregate(Schema slot) {
            Schema target = slot.isMember() ? slot.memberTarget() : slot;
            if (target.type() != ShapeType.LIST) {
                throw new UnsupportedSchemaException(
                        "XML runtime codegen cannot write " + target.type() + " at " + slot.id());
            }
            Tags tags = new Tags(itemName(slot), directNamespace(slot.listMember()));
            ListKey key = new ListKey(target.id(), tags);
            Integer existing = listIds.get(key);
            if (existing != null) {
                return existing;
            }
            int id = lists.size();
            listIds.put(key, id);
            lists.add(new ListAggregate(id, slot, tags));
            return id;
        }

        private int mapAggregate(Schema slot) {
            Schema target = slot.isMember() ? slot.memberTarget() : slot;
            if (target.type() != ShapeType.MAP) {
                throw new UnsupportedSchemaException(
                        "XML runtime codegen cannot write " + target.type() + " at " + slot.id());
            }
            if (slot.mapKeyMember().type() != ShapeType.STRING) {
                // Generated map writers read the key straight out of Map.Entry, so anything but a
                // String-typed key would need a conversion the dispatch path does not describe.
                throw new UnsupportedSchemaException(
                        "XML runtime codegen only writes string map keys, found "
                                + slot.mapKeyMember().type() + " at " + slot.id());
            }
            String entryName;
            String keyName;
            String valueName;
            var extension = slot.getExtension(XmlSchemaExtensions.KEY);
            if (extension instanceof XmlSchemaExtensions.MapExtension me) {
                entryName = new String(me.entryNameBytes(), StandardCharsets.UTF_8);
                keyName = new String(me.keyNameBytes(), StandardCharsets.UTF_8);
                valueName = new String(me.valueNameBytes(), StandardCharsets.UTF_8);
            } else {
                XmlInfo.MapMemberInfo info = xmlInfo.getMapInfo(slot);
                entryName = info.entryName;
                keyName = info.keyName;
                valueName = info.valueName;
            }
            String keyNamespace = directNamespace(slot.mapKeyMember());
            Tags valueTags = new Tags(valueName, directNamespace(slot.mapValueMember()));
            MapKey key = new MapKey(target.id(), entryName, keyName, keyNamespace, valueTags);
            Integer existing = mapIds.get(key);
            if (existing != null) {
                return existing;
            }
            int id = maps.size();
            mapIds.put(key, id);
            maps.add(new MapAggregate(id, slot, entryName, keyName, keyNamespace, valueTags));
            return id;
        }

        private String itemName(Schema slot) {
            var extension = slot.getExtension(XmlSchemaExtensions.KEY);
            if (extension instanceof XmlSchemaExtensions.ListExtension le) {
                return new String(le.memberNameBytes(), StandardCharsets.UTF_8);
            }
            return xmlInfo.getListInfo(slot).memberName;
        }

        // ------------------------------------------------------------------------------------------
        // Readers
        // ------------------------------------------------------------------------------------------

        /**
         * Emits the reader for one structure or union: attributes first, then a loop over children.
         *
         * <p>Entry and exit match {@code readStructContent} exactly. The container element is already
         * parsed when this runs, and its end tag is consumed here.
         *
         * <p>Dispatch is a switch on the element name's byte length and then a packed-word compare, so a
         * child's name never becomes a {@code String} on the way in. The interpreted path instead walks
         * an {@link XmlMemberLookup} keyed on the same resolved names; the names come from the same
         * table, so the two agree on every match and mismatch.
         *
         * <p>A flattened aggregate is accumulated in place, in a local, as its elements arrive. The
         * interpreted path records byte spans and replays them through a fresh sub-deserializer once the
         * container closes. Both build the same list, but this makes a single pass and allocates neither
         * the span lists nor the sub-deserializers.
         */
        private void emitReader(RuntimeCodecPlan.StructPlan structure) {
            var extension = structExtension(structure.schema());
            List<RuntimeCodecPlan.MemberPlan> attributes = new ArrayList<>();
            List<RuntimeCodecPlan.MemberPlan> elements = new ArrayList<>();
            List<RuntimeCodecPlan.MemberPlan> flattened = new ArrayList<>();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                if (extension != null && isAttribute(extension, member.schema())) {
                    attributes.add(member);
                    continue;
                }
                elements.add(member);
                if (isFlattenedMember(extension, member.schema())) {
                    switch (member.target().type()) {
                        case LIST, SET, MAP -> flattened.add(member);
                        // Matches the writer: bare text with no element around it is a modelling error,
                        // and inventing a reading for it would be worse than falling back.
                        default -> throw new UnsupportedSchemaException(
                                "@xmlFlattened on non-aggregate member " + member.schema().id());
                    }
                }
            }
            int firstAccumulator = FIRST_ACCUMULATOR_LOCAL;
            int scratch = firstAccumulator + flattened.size();

            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    readerName(structure),
                    readerDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedEnterContainer", "()J", false);
            method.visitVarInsn(LSTORE, 3);

            if (!attributes.isEmpty()) {
                // The interpreted path skips the whole attribute pass when the element carries none,
                // and so does this: one branch instead of a lookup per modeled attribute.
                Label noAttributes = new Label();
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedHasAttributes", "()Z", false);
                method.visitJumpInsn(IFEQ, noAttributes);
                emitReadAttributes(method, structure, extension, attributes, scratch);
                method.visitLabel(noAttributes);
            }

            for (int i = 0; i < flattened.size(); i++) {
                method.visitInsn(ACONST_NULL);
                method.visitVarInsn(ASTORE, firstAccumulator + i);
            }

            Label loop = new Label();
            Label exit = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNextElement", "()Z", false);
            method.visitJumpInsn(IFEQ, exit);
            emitDispatch(method, structure, extension, elements, flattened, firstAccumulator, scratch);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(exit);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(LLOAD, 3);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedExitContainer", "(J)V", false);

            // A flattened member is set once, after the container closes, which is also when the
            // interpreted path replays its spans. An absent member is left unset rather than set to an
            // empty collection.
            for (int i = 0; i < flattened.size(); i++) {
                RuntimeCodecPlan.MemberPlan member = flattened.get(i);
                Label absent = new Label();
                method.visitVarInsn(ALOAD, firstAccumulator + i);
                method.visitJumpInsn(IFNULL, absent);
                method.visitVarInsn(ALOAD, 2);
                method.visitVarInsn(ALOAD, firstAccumulator + i);
                method.visitTypeInsn(
                        CHECKCAST,
                        Type.getInternalName(member.setter().getParameterTypes()[0]));
                emitSetter(method, member);
                method.visitLabel(absent);
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        /**
         * Emits the element dispatch for one iteration of a structure's child loop.
         *
         * <p>On entry the reader is positioned on a start element; on exit that element has been
         * consumed either by a member's read or by {@code generatedSkipElement}.
         */
        private void emitDispatch(
                MethodVisitor method,
                RuntimeCodecPlan.StructPlan structure,
                XmlSchemaExtensions.StructExtension extension,
                List<RuntimeCodecPlan.MemberPlan> elements,
                List<RuntimeCodecPlan.MemberPlan> flattened,
                int firstAccumulator,
                int scratch
        ) {
            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new TreeMap<>();
            for (RuntimeCodecPlan.MemberPlan member : elements) {
                int length = nameBytes(extension, member.schema()).length;
                groups.computeIfAbsent(length, ignored -> new ArrayList<>()).add(member);
            }
            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> outlined = new LinkedHashMap<>();
            Label matched = new Label();
            Label unknown = new Label();
            if (!groups.isEmpty()) {
                int[] keys = new int[groups.size()];
                Label[] labels = new Label[groups.size()];
                int index = 0;
                for (Integer length : groups.keySet()) {
                    keys[index] = length;
                    labels[index] = new Label();
                    index++;
                }
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNameLength", "()I", false);
                method.visitLookupSwitchInsn(unknown, keys, labels);
                index = 0;
                for (Map.Entry<Integer, List<RuntimeCodecPlan.MemberPlan>> group : groups.entrySet()) {
                    method.visitLabel(labels[index++]);
                    List<RuntimeCodecPlan.MemberPlan> members = group.getValue();
                    if (outlineGroup(structure, members, flattened)) {
                        outlined.put(group.getKey(), members);
                        method.visitVarInsn(ALOAD, 0);
                        method.visitVarInsn(ALOAD, 1);
                        method.visitVarInsn(ALOAD, 2);
                        method.visitMethodInsn(
                                INVOKESPECIAL,
                                className,
                                readerGroupName(structure, group.getKey()),
                                readerGroupDescriptor(structure),
                                false);
                        method.visitJumpInsn(IFNE, matched);
                    } else {
                        for (RuntimeCodecPlan.MemberPlan member : members) {
                            Label next = new Label();
                            emitNameEquals(method, nameBytes(extension, member.schema()));
                            method.visitJumpInsn(IFEQ, next);
                            emitReadMember(method, member, flattened, firstAccumulator, scratch);
                            method.visitJumpInsn(GOTO, matched);
                            method.visitLabel(next);
                        }
                    }
                    method.visitJumpInsn(GOTO, unknown);
                }
            }
            method.visitLabel(unknown);
            if (structure.union()) {
                // A structure drops an unrecognized element, but a union has to remember that it saw
                // one: the interpreted path's unknownMember does the same, and losing it would turn an
                // unknown variant into a builder with nothing set.
                method.visitVarInsn(ALOAD, 2);
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        "generatedElementName",
                        "()Ljava/lang/String;",
                        false);
                Method unknownMember = unknownMember(structure);
                invoke(method, unknownMember);
                popResult(method, unknownMember.getReturnType());
            }
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipElement", "()V", false);
            method.visitLabel(matched);

            for (Map.Entry<Integer, List<RuntimeCodecPlan.MemberPlan>> group : outlined.entrySet()) {
                emitReaderGroup(structure, extension, group.getKey(), group.getValue());
            }
        }

        /**
         * Whether a length group's name comparisons belong in their own method.
         *
         * <p>Only worth it for a structure the plan already considers too big for one reader, and only
         * for a group that has something to choose between. A group holding a flattened member is never
         * outlined: its accumulator is a local of the structure reader.
         */
        private boolean outlineGroup(
                RuntimeCodecPlan.StructPlan structure,
                List<RuntimeCodecPlan.MemberPlan> members,
                List<RuntimeCodecPlan.MemberPlan> flattened
        ) {
            if (structure.readerBuckets() <= 1 || members.size() < 2) {
                return false;
            }
            for (RuntimeCodecPlan.MemberPlan member : members) {
                if (flattened.contains(member)) {
                    return false;
                }
            }
            return true;
        }

        /** @return true when one of this group's members matched and consumed the element. */
        private void emitReaderGroup(
                RuntimeCodecPlan.StructPlan structure,
                XmlSchemaExtensions.StructExtension extension,
                int length,
                List<RuntimeCodecPlan.MemberPlan> members
        ) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    readerGroupName(structure, length),
                    readerGroupDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            for (RuntimeCodecPlan.MemberPlan member : members) {
                Label next = new Label();
                emitNameEquals(method, nameBytes(extension, member.schema()));
                method.visitJumpInsn(IFEQ, next);
                method.visitVarInsn(ALOAD, 2);
                emitReadValue(method, member.schema(), member.setter().getParameterTypes()[0], 3);
                emitSetter(method, member);
                method.visitInsn(ICONST_1);
                method.visitInsn(IRETURN);
                method.visitLabel(next);
            }
            method.visitInsn(ICONST_0);
            method.visitInsn(IRETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitReadMember(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                List<RuntimeCodecPlan.MemberPlan> flattened,
                int firstAccumulator,
                int scratch
        ) {
            int accumulator = flattened.indexOf(member);
            if (accumulator >= 0) {
                emitAccumulateFlattened(method, member, firstAccumulator + accumulator);
                return;
            }
            method.visitVarInsn(ALOAD, 2);
            emitReadValue(method, member.schema(), member.setter().getParameterTypes()[0], scratch);
            emitSetter(method, member);
        }

        /**
         * Extends a flattened member's collection with the element the reader is positioned on.
         *
         * <p>The collection lives in a local and is created on first sight, so a member that never
         * appears costs nothing and a member that appears once allocates once.
         */
        private void emitAccumulateFlattened(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                int slot
        ) {
            boolean map = member.target().type() == ShapeType.MAP;
            String concrete = map ? "java/util/LinkedHashMap" : "java/util/ArrayList";
            String erased = map ? "java/util/Map" : "java/util/List";
            int aggregate = map ? mapAggregate(member.schema()) : listAggregate(member.schema());
            Label have = new Label();
            method.visitVarInsn(ALOAD, slot);
            method.visitJumpInsn(IFNONNULL, have);
            method.visitTypeInsn(NEW, concrete);
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, concrete, "<init>", "()V", false);
            method.visitVarInsn(ASTORE, slot);
            method.visitLabel(have);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, slot);
            method.visitTypeInsn(CHECKCAST, erased);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    map ? "readM" + aggregate + "E" : "readL" + aggregate + "I",
                    "(L" + READER + ";L" + erased + ";)V",
                    false);
        }

        private void emitReadAttributes(
                MethodVisitor method,
                RuntimeCodecPlan.StructPlan structure,
                XmlSchemaExtensions.StructExtension extension,
                List<RuntimeCodecPlan.MemberPlan> attributes,
                int scratch
        ) {
            if (attributes.size() <= MAX_ATTRIBUTES_PER_METHOD) {
                for (RuntimeCodecPlan.MemberPlan member : attributes) {
                    emitReadAttribute(method, extension, member, scratch);
                }
                return;
            }
            int chunk = 0;
            for (int start = 0; start < attributes.size(); start += MAX_ATTRIBUTES_PER_METHOD, chunk++) {
                method.visitVarInsn(ALOAD, 0);
                method.visitVarInsn(ALOAD, 1);
                method.visitVarInsn(ALOAD, 2);
                method.visitMethodInsn(
                        INVOKESPECIAL,
                        className,
                        readerAttributeName(structure, chunk),
                        readerDescriptor(structure),
                        false);
            }
            chunk = 0;
            for (int start = 0; start < attributes.size(); start += MAX_ATTRIBUTES_PER_METHOD, chunk++) {
                int end = Math.min(start + MAX_ATTRIBUTES_PER_METHOD, attributes.size());
                MethodVisitor chunkMethod = writer.visitMethod(
                        ACC_PRIVATE,
                        readerAttributeName(structure, chunk),
                        readerDescriptor(structure),
                        null,
                        null);
                chunkMethod.visitCode();
                for (int i = start; i < end; i++) {
                    emitReadAttribute(chunkMethod, extension, attributes.get(i), 3);
                }
                chunkMethod.visitInsn(RETURN);
                chunkMethod.visitMaxs(0, 0);
                chunkMethod.visitEnd();
                methodCount++;
            }
        }

        /**
         * Reads one attribute by its resolved name, mirroring {@code AttributeDeserializer}.
         *
         * <p>An absent attribute leaves the member unset, which is what the interpreted path does by
         * only calling the consumer for a non-null lookup.
         */
        private void emitReadAttribute(
                MethodVisitor method,
                XmlSchemaExtensions.StructExtension extension,
                RuntimeCodecPlan.MemberPlan member,
                int scratch
        ) {
            method.visitVarInsn(ALOAD, 1);
            pushConstant(method, constant(attributeLookupName(memberName(extension, member.schema()))));
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    "generatedAttribute",
                    "([B)Ljava/lang/String;",
                    false);
            method.visitVarInsn(ASTORE, scratch);
            Label absent = new Label();
            method.visitVarInsn(ALOAD, scratch);
            method.visitJumpInsn(IFNULL, absent);
            Class<?> wanted = member.setter().getParameterTypes()[0];
            method.visitVarInsn(ALOAD, 2);
            switch (member.target().type()) {
                case STRING -> method.visitVarInsn(ALOAD, scratch);
                case ENUM -> {
                    method.visitVarInsn(ALOAD, scratch);
                    emitEnumMap(method, member.target());
                }
                case BOOLEAN -> {
                    method.visitVarInsn(ALOAD, scratch);
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            READER,
                            "generatedAttrBoolean",
                            "(Ljava/lang/String;)Z",
                            false);
                    box(method, boolean.class, wanted);
                }
                case INTEGER -> {
                    method.visitVarInsn(ALOAD, scratch);
                    parse(method, "java/lang/Integer", "parseInt", "I");
                    box(method, int.class, wanted);
                }
                case INT_ENUM -> {
                    method.visitVarInsn(ALOAD, scratch);
                    parse(method, "java/lang/Integer", "parseInt", "I");
                    emitIntEnumMap(method, member.target());
                }
                case LONG -> {
                    method.visitVarInsn(ALOAD, scratch);
                    parse(method, "java/lang/Long", "parseLong", "J");
                    box(method, long.class, wanted);
                }
                case FLOAT -> {
                    method.visitVarInsn(ALOAD, scratch);
                    parse(method, "java/lang/Float", "parseFloat", "F");
                    box(method, float.class, wanted);
                }
                case DOUBLE -> {
                    method.visitVarInsn(ALOAD, scratch);
                    parse(method, "java/lang/Double", "parseDouble", "D");
                    box(method, double.class, wanted);
                }
                case TIMESTAMP -> {
                    method.visitVarInsn(ALOAD, scratch);
                    method.visitLdcInsn(timestampFormat(member.schema()));
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            READER,
                            "generatedAttrTimestamp",
                            "(Ljava/lang/String;I)L" + INSTANT + ";",
                            false);
                }
                default -> throw new UnsupportedSchemaException(
                        "XML runtime codegen cannot read " + member.target().type()
                                + " from an attribute at " + member.schema().id());
            }
            emitSetter(method, member);
            method.visitLabel(absent);
        }

        /**
         * Reads the element the reader is positioned on and leaves its value on the stack.
         *
         * @param wanted the type the value is about to be handed to, which decides whether a primitive
         *               read is boxed.
         * @param scratch a free local this may use, and does not leave live.
         */
        private void emitReadValue(MethodVisitor method, Schema slot, Class<?> wanted, int scratch) {
            Schema target = slot.isMember() ? slot.memberTarget() : slot;
            switch (target.type()) {
                case BOOLEAN -> {
                    read(method, "generatedReadBoolean", "()Z");
                    box(method, boolean.class, wanted);
                }
                case BYTE -> {
                    read(method, "generatedReadByte", "()B");
                    box(method, byte.class, wanted);
                }
                case SHORT -> {
                    read(method, "generatedReadShort", "()S");
                    box(method, short.class, wanted);
                }
                case INTEGER -> {
                    read(method, "generatedReadInteger", "()I");
                    box(method, int.class, wanted);
                }
                case LONG -> {
                    read(method, "generatedReadLong", "()J");
                    box(method, long.class, wanted);
                }
                case FLOAT -> {
                    read(method, "generatedReadFloat", "()F");
                    box(method, float.class, wanted);
                }
                case DOUBLE -> {
                    read(method, "generatedReadDouble", "()D");
                    box(method, double.class, wanted);
                }
                case BIG_INTEGER -> read(method, "generatedReadBigInteger", "()Ljava/math/BigInteger;");
                case BIG_DECIMAL -> read(method, "generatedReadBigDecimal", "()Ljava/math/BigDecimal;");
                case STRING -> read(method, "generatedReadString", "()Ljava/lang/String;");
                case ENUM -> {
                    read(method, "generatedReadString", "()Ljava/lang/String;");
                    emitEnumMap(method, target);
                }
                case INT_ENUM -> {
                    read(method, "generatedReadInteger", "()I");
                    emitIntEnumMap(method, target);
                }
                case BLOB -> read(method, "generatedReadBlob", "()L" + BYTE_BUFFER + ";");
                case TIMESTAMP -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitLdcInsn(timestampFormat(slot));
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadTimestamp",
                            "(I)L" + INSTANT + ";",
                            false);
                }
                case LIST, SET -> {
                    if (!wanted.isAssignableFrom(ArrayList.class)) {
                        throw new UnsupportedSchemaException(
                                "XML runtime codegen reads lists as ArrayList, which " + wanted.getName()
                                        + " at " + slot.id() + " cannot hold");
                    }
                    int aggregate = listAggregate(slot);
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            "readL" + aggregate,
                            "(L" + READER + ";)Ljava/util/List;",
                            false);
                }
                case MAP -> {
                    if (!wanted.isAssignableFrom(LinkedHashMap.class)) {
                        throw new UnsupportedSchemaException(
                                "XML runtime codegen reads maps as LinkedHashMap, which " + wanted.getName()
                                        + " at " + slot.id() + " cannot hold");
                    }
                    int aggregate = mapAggregate(slot);
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            "readM" + aggregate,
                            "(L" + READER + ";)Ljava/util/Map;",
                            false);
                }
                case STRUCTURE, UNION -> emitNestedRead(method, target, scratch);
                default -> throw new UnsupportedSchemaException(
                        "XML runtime codegen cannot read " + target.type() + " at " + slot.id());
            }
        }

        private void emitNestedRead(MethodVisitor method, Schema target, int scratch) {
            RuntimeCodecPlan.StructPlan nested = structuresById.get(target.id());
            if (nested == null) {
                throw new UnsupportedSchemaException("Unplanned structure " + target.id());
            }
            invoke(method, nested.builderFactory());
            if (nested.builderFactory().getReturnType() != nested.builderClass()) {
                method.visitTypeInsn(CHECKCAST, Type.getInternalName(nested.builderClass()));
            }
            method.visitVarInsn(ASTORE, scratch);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, scratch);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    readerName(nested),
                    readerDescriptor(nested),
                    false);
            method.visitVarInsn(ALOAD, scratch);
            invoke(method, findBuild(nested.builderClass(), nested.shapeClass()));
        }

        /**
         * The entry point declared by {@link GeneratedXmlCodec#read}.
         *
         * <p>The type test is what lets a registry keyed on a shape class stay honest: a caller that
         * hands over some other builder gets false and the interpreted path runs, with nothing consumed.
         */
        private void emitReadEntry(RuntimeCodecPlan.StructPlan root) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "read",
                    "(L" + READER + ";L" + SHAPE_BUILDER + ";)Z",
                    null,
                    null);
            method.visitCode();
            String builderType = Type.getInternalName(root.builderClass());
            Label mine = new Label();
            method.visitVarInsn(ALOAD, 2);
            method.visitTypeInsn(INSTANCEOF, builderType);
            method.visitJumpInsn(IFNE, mine);
            method.visitInsn(ICONST_0);
            method.visitInsn(IRETURN);
            method.visitLabel(mine);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, 2);
            method.visitTypeInsn(CHECKCAST, builderType);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    readerName(root),
                    readerDescriptor(root),
                    false);
            method.visitInsn(ICONST_1);
            method.visitInsn(IRETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        // ------------------------------------------------------------------------------------------
        // Aggregate readers
        // ------------------------------------------------------------------------------------------

        /** Reads a wrapped list: the container element is the member's, its children are the items. */
        private void emitListReader(ListAggregate aggregate) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "readL" + aggregate.id(),
                    "(L" + READER + ";)Ljava/util/List;",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedEnterContainer", "()J", false);
            method.visitVarInsn(LSTORE, 2);
            // ArrayList with no capacity hint, because XML never reports a container size and the
            // interpreted path therefore takes the same branch.
            method.visitTypeInsn(NEW, "java/util/ArrayList");
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
            method.visitVarInsn(ASTORE, 4);
            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNextElement", "()Z", false);
            method.visitJumpInsn(IFEQ, done);
            Label expected = new Label();
            emitNameEquals(method, aggregate.tags().name().getBytes(StandardCharsets.UTF_8));
            method.visitJumpInsn(IFNE, expected);
            emitElementNameThrow(
                    method,
                    "Expected list item '" + aggregate.tags().name() + "' but found '",
                    "'");
            method.visitLabel(expected);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    "readL" + aggregate.id() + "I",
                    "(L" + READER + ";Ljava/util/List;)V",
                    false);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(LLOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedExitContainer", "(J)V", false);
            method.visitVarInsn(ALOAD, 4);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        /**
         * Reads one item into an existing list.
         *
         * <p>Shared by the wrapped and flattened forms, which differ only in what surrounds the item:
         * either way the reader is positioned on the item's element when this is entered.
         */
        private void emitListItemReader(ListAggregate aggregate) {
            Schema item = aggregate.slot().listMember();
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "readL" + aggregate.id() + "I",
                    "(L" + READER + ";Ljava/util/List;)V",
                    null,
                    null);
            method.visitCode();
            Label present = new Label();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedIsNull", "()Z", false);
            method.visitJumpInsn(IFEQ, present);
            if (isSparse(aggregate.slot())) {
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipNull", "()V", false);
                method.visitVarInsn(ALOAD, 2);
                method.visitInsn(ACONST_NULL);
                method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
                method.visitInsn(POP);
                method.visitInsn(RETURN);
            } else {
                emitThrow(method, "Null value found in dense list");
            }
            method.visitLabel(present);
            method.visitVarInsn(ALOAD, 2);
            emitReadValue(method, item, Object.class, 3);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
            method.visitInsn(POP);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        /**
         * Reads a wrapped map: the container element is the member's, its children are entries.
         *
         * <p>A child that is not an entry stops the loop rather than throwing, which is what
         * {@code readMapContent} does for a non-flattened map. The end-tag check that follows then
         * reports it.
         */
        private void emitMapReader(MapAggregate aggregate) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "readM" + aggregate.id(),
                    "(L" + READER + ";)Ljava/util/Map;",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedEnterContainer", "()J", false);
            method.visitVarInsn(LSTORE, 2);
            method.visitTypeInsn(NEW, "java/util/LinkedHashMap");
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
            method.visitVarInsn(ASTORE, 4);
            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNextElement", "()Z", false);
            method.visitJumpInsn(IFEQ, done);
            emitNameEquals(method, aggregate.entryName().getBytes(StandardCharsets.UTF_8));
            method.visitJumpInsn(IFEQ, done);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    "readM" + aggregate.id() + "E",
                    "(L" + READER + ";Ljava/util/Map;)V",
                    false);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(LLOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedExitContainer", "(J)V", false);
            method.visitVarInsn(ALOAD, 4);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        /**
         * Reads one entry into an existing map, from the entry element the reader is positioned on.
         *
         * <p>The entry name is baked rather than passed, because a map's aggregate id already includes
         * it: a flattened member and a wrapped member of the same map shape get different ids.
         *
         * <p>The entry's end tag is consumed here. That matches {@code readMapContent} but is stricter
         * than the flattened replay path, which reads an isolated span and so never validates it.
         */
        private void emitMapEntryReader(MapAggregate aggregate) {
            Schema value = aggregate.slot().mapValueMember();
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "readM" + aggregate.id() + "E",
                    "(L" + READER + ";Ljava/util/Map;)V",
                    null,
                    null);
            method.visitCode();

            Label haveKey = new Label();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNextElement", "()Z", false);
            method.visitJumpInsn(IFNE, haveKey);
            emitThrow(method, "Expected map key, but map unexpectedly closed");
            method.visitLabel(haveKey);
            Label keyMatched = new Label();
            emitNameEquals(method, aggregate.keyName().getBytes(StandardCharsets.UTF_8));
            method.visitJumpInsn(IFNE, keyMatched);
            emitElementNameThrow(method, "Expected map key but found '", "'");
            method.visitLabel(keyMatched);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedReadKey", "()Ljava/lang/String;", false);
            method.visitVarInsn(ASTORE, 3);

            Label haveValue = new Label();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNextElement", "()Z", false);
            method.visitJumpInsn(IFNE, haveValue);
            emitThrow(method, "Expected map value, but map unexpectedly closed");
            method.visitLabel(haveValue);
            Label valueMatched = new Label();
            emitNameEquals(method, aggregate.valueTags().name().getBytes(StandardCharsets.UTF_8));
            method.visitJumpInsn(IFNE, valueMatched);
            emitElementNameThrow(method, "Expected map value but found '", "'");
            method.visitLabel(valueMatched);

            Label stored = new Label();
            Label present = new Label();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedIsNull", "()Z", false);
            method.visitJumpInsn(IFEQ, present);
            if (isSparse(aggregate.slot())) {
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipNull", "()V", false);
                method.visitVarInsn(ALOAD, 2);
                method.visitVarInsn(ALOAD, 3);
                method.visitInsn(ACONST_NULL);
                emitMapPut(method);
                method.visitJumpInsn(GOTO, stored);
            } else {
                emitThrow(method, "Null value found in dense map");
            }
            method.visitLabel(present);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 3);
            emitReadValue(method, value, Object.class, 4);
            emitMapPut(method);
            method.visitLabel(stored);

            method.visitVarInsn(ALOAD, 1);
            pushConstant(method, constant(aggregate.entryName()));
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedEndEntry", "([B)V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private static void emitMapPut(MethodVisitor method) {
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map",
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    true);
            method.visitInsn(POP);
        }

        // ------------------------------------------------------------------------------------------
        // Enum mappers
        // ------------------------------------------------------------------------------------------

        /**
         * Emits one string-to-constant mapper per enum shape.
         *
         * <p>A {@code hashCode} switch narrows to at most a handful of candidates and each is then
         * confirmed with {@code String.equals}, so a hash collision picks the right constant instead of
         * the first one that hashed the same.
         */
        private void emitEnumReaders() {
            for (Map.Entry<Class<?>, Integer> entry : enumIds.entrySet()) {
                Class<?> enumClass = entry.getKey();
                Method unknown;
                try {
                    unknown = enumClass.getMethod("unknown", String.class);
                } catch (NoSuchMethodException e) {
                    throw new UnsupportedSchemaException(
                            "Generated enum lacks an unknown method: " + enumClass.getName());
                }
                String enumType = Type.getInternalName(enumClass);
                MethodVisitor method = writer.visitMethod(
                        ACC_PRIVATE | ACC_STATIC,
                        "mapE" + entry.getValue(),
                        "(Ljava/lang/String;)L" + enumType + ";",
                        null,
                        null);
                method.visitCode();
                Map<Integer, List<EnumConstant>> groups = new TreeMap<>();
                for (EnumConstant constant : enumConstants(enumClass)) {
                    groups.computeIfAbsent(constant.value().hashCode(), ignored -> new ArrayList<>())
                            .add(constant);
                }
                Label unknownValue = new Label();
                if (!groups.isEmpty()) {
                    int[] keys = new int[groups.size()];
                    Label[] labels = new Label[groups.size()];
                    int index = 0;
                    for (Integer hash : groups.keySet()) {
                        keys[index] = hash;
                        labels[index] = new Label();
                        index++;
                    }
                    method.visitVarInsn(ALOAD, 0);
                    method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
                    method.visitLookupSwitchInsn(unknownValue, keys, labels);
                    index = 0;
                    for (Map.Entry<Integer, List<EnumConstant>> group : groups.entrySet()) {
                        method.visitLabel(labels[index++]);
                        for (EnumConstant constant : group.getValue()) {
                            Label next = new Label();
                            method.visitLdcInsn(constant.value());
                            method.visitVarInsn(ALOAD, 0);
                            method.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    "java/lang/String",
                                    "equals",
                                    "(Ljava/lang/Object;)Z",
                                    false);
                            method.visitJumpInsn(IFEQ, next);
                            method.visitFieldInsn(
                                    GETSTATIC,
                                    enumType,
                                    constant.field().getName(),
                                    "L" + enumType + ";");
                            method.visitInsn(ARETURN);
                            method.visitLabel(next);
                        }
                        method.visitJumpInsn(GOTO, unknownValue);
                    }
                }
                method.visitLabel(unknownValue);
                method.visitVarInsn(ALOAD, 0);
                invoke(method, unknown);
                method.visitInsn(ARETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                methodCount++;
            }
        }

        /**
         * Emits one int-to-constant mapper per intEnum shape.
         *
         * <p>Values need not be dense, so this is a {@code lookupswitch}; the encoder picks the denser
         * form when the keys allow it.
         */
        private void emitIntEnumReaders() {
            for (Map.Entry<Class<?>, Integer> entry : intEnumIds.entrySet()) {
                Class<?> enumClass = entry.getKey();
                Method unknown;
                try {
                    unknown = enumClass.getMethod("unknown", int.class);
                } catch (NoSuchMethodException e) {
                    throw new UnsupportedSchemaException(
                            "Generated intEnum lacks an unknown method: " + enumClass.getName());
                }
                String enumType = Type.getInternalName(enumClass);
                MethodVisitor method = writer.visitMethod(
                        ACC_PRIVATE | ACC_STATIC,
                        "mapI" + entry.getValue(),
                        "(I)L" + enumType + ";",
                        null,
                        null);
                method.visitCode();
                List<IntEnumConstant> constants = intEnumConstants(enumClass);
                Label unknownValue = new Label();
                if (!constants.isEmpty()) {
                    int[] keys = new int[constants.size()];
                    Label[] labels = new Label[constants.size()];
                    for (int i = 0; i < constants.size(); i++) {
                        keys[i] = constants.get(i).value();
                        labels[i] = new Label();
                    }
                    method.visitVarInsn(ILOAD, 0);
                    method.visitLookupSwitchInsn(unknownValue, keys, labels);
                    for (int i = 0; i < constants.size(); i++) {
                        method.visitLabel(labels[i]);
                        method.visitFieldInsn(
                                GETSTATIC,
                                enumType,
                                constants.get(i).field().getName(),
                                "L" + enumType + ";");
                        method.visitInsn(ARETURN);
                    }
                }
                method.visitLabel(unknownValue);
                method.visitVarInsn(ILOAD, 0);
                invoke(method, unknown);
                method.visitInsn(ARETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                methodCount++;
            }
        }

        private void emitEnumMap(MethodVisitor method, Schema target) {
            Class<?> enumClass = enumClass(target);
            Integer id = enumIds.get(enumClass);
            if (id == null) {
                id = enumIds.size();
                enumIds.put(enumClass, id);
            }
            method.visitMethodInsn(
                    INVOKESTATIC,
                    className,
                    "mapE" + id,
                    "(Ljava/lang/String;)L" + Type.getInternalName(enumClass) + ";",
                    false);
        }

        private void emitIntEnumMap(MethodVisitor method, Schema target) {
            Class<?> enumClass = enumClass(target);
            Integer id = intEnumIds.get(enumClass);
            if (id == null) {
                id = intEnumIds.size();
                intEnumIds.put(enumClass, id);
            }
            method.visitMethodInsn(
                    INVOKESTATIC,
                    className,
                    "mapI" + id,
                    "(I)L" + Type.getInternalName(enumClass) + ";",
                    false);
        }

        private static Class<?> enumClass(Schema target) {
            Class<?> shapeClass = target.shapeClass();
            if (shapeClass == null) {
                throw new UnsupportedSchemaException("No Java class for enum " + target.id());
            }
            return shapeClass;
        }

        private static List<EnumConstant> enumConstants(Class<?> enumClass) {
            List<EnumConstant> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Field field : enumClass.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == enumClass) {
                    String value;
                    try {
                        value = ((SmithyEnum) field.get(null)).getValue();
                    } catch (IllegalAccessException e) {
                        throw new UnsupportedSchemaException(
                                "Cannot access enum constant " + enumClass.getName() + "." + field.getName());
                    }
                    // Two constants sharing a value would leave the equals chain unreachable rather
                    // than wrong, but keeping the first is what the switch shape assumes.
                    if (seen.add(value)) {
                        result.add(new EnumConstant(field, value));
                    }
                }
            }
            result.sort(Comparator.comparing(constant -> constant.field().getName()));
            return result;
        }

        private static List<IntEnumConstant> intEnumConstants(Class<?> enumClass) {
            List<IntEnumConstant> result = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (Field field : enumClass.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == enumClass) {
                    int value;
                    try {
                        value = ((SmithyIntEnum) field.get(null)).getValue();
                    } catch (IllegalAccessException e) {
                        throw new UnsupportedSchemaException(
                                "Cannot access intEnum constant " + enumClass.getName() + "."
                                        + field.getName());
                    }
                    // lookupswitch keys have to be unique, so an aliased constant falls through to
                    // unknown(int) instead of producing a class that fails verification.
                    if (seen.add(value)) {
                        result.add(new IntEnumConstant(field, value));
                    }
                }
            }
            result.sort(Comparator.comparingInt(IntEnumConstant::value));
            return result;
        }

        // ------------------------------------------------------------------------------------------
        // Reader bytecode helpers
        // ------------------------------------------------------------------------------------------

        /** Leaves a boolean on the stack: whether the current element's name is {@code name}. */
        private void emitNameEquals(MethodVisitor method, byte[] name) {
            method.visitVarInsn(ALOAD, 1);
            if (name.length <= Long.BYTES) {
                method.visitLdcInsn(packLittleEndian(name));
                method.visitLdcInsn(lowByteMask(name.length));
                method.visitLdcInsn(name.length);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNameEquals8", "(JJI)Z", false);
            } else if (name.length <= Long.BYTES * 2) {
                method.visitLdcInsn(packLittleEndian(name, 0, Long.BYTES));
                method.visitLdcInsn(packLittleEndian(name, Long.BYTES, name.length - Long.BYTES));
                method.visitLdcInsn(lowByteMask(name.length - Long.BYTES));
                method.visitLdcInsn(name.length);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNameEquals16", "(JJJI)Z", false);
            } else {
                pushConstant(method, constant(new String(name, StandardCharsets.UTF_8)));
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedNameEquals", "([B)Z", false);
            }
        }

        /**
         * Throws with the offending element's name in the message.
         *
         * <p>{@code emitThrow} only takes a constant, so the message is assembled with
         * {@code String.concat} rather than a builder: two calls on a path that ends in a throw.
         */
        private static void emitElementNameThrow(MethodVisitor method, String prefix, String suffix) {
            method.visitTypeInsn(NEW, SERIALIZATION_EXCEPTION);
            method.visitInsn(DUP);
            method.visitLdcInsn(prefix);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    "generatedElementName",
                    "()Ljava/lang/String;",
                    false);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/String",
                    "concat",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false);
            method.visitLdcInsn(suffix);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/String",
                    "concat",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    SERIALIZATION_EXCEPTION,
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false);
            method.visitInsn(ATHROW);
        }

        private static void read(MethodVisitor method, String name, String descriptor) {
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, name, descriptor, false);
        }

        private static void parse(MethodVisitor method, String owner, String name, String returns) {
            method.visitMethodInsn(INVOKESTATIC, owner, name, "(Ljava/lang/String;)" + returns, false);
        }

        private static void box(MethodVisitor method, Class<?> primitive, Class<?> wanted) {
            if (wanted.isPrimitive()) {
                return;
            }
            String boxed = Type.getInternalName(BOXES.get(primitive));
            method.visitMethodInsn(
                    INVOKESTATIC,
                    boxed,
                    "valueOf",
                    "(" + Type.getDescriptor(primitive) + ")L" + boxed + ";",
                    false);
        }

        private void emitSetter(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            invoke(method, member.setter());
            popResult(method, member.setter().getReturnType());
        }

        private static void popResult(MethodVisitor method, Class<?> returnType) {
            if (returnType == void.class) {
                return;
            }
            if (returnType == long.class || returnType == double.class) {
                // POP2 is not in the emitter's instruction set, and no generated builder returns a
                // two-slot value, so this is a guard rather than a case to handle.
                throw new UnsupportedSchemaException("Builder setter returns " + returnType);
            }
            method.visitInsn(POP);
        }

        private static Method unknownMember(RuntimeCodecPlan.StructPlan structure) {
            try {
                return structure.builderClass().getMethod("$unknownMember", String.class);
            } catch (NoSuchMethodException e) {
                throw new UnsupportedSchemaException(
                        "Union builder cannot record an unknown variant: " + structure.schema().id());
            }
        }

        private static boolean isSparse(Schema slot) {
            Schema target = slot.isMember() ? slot.memberTarget() : slot;
            return target.hasTrait(TraitKey.SPARSE_TRAIT);
        }

        private static byte[] nameBytes(XmlSchemaExtensions.StructExtension extension, Schema member) {
            return memberName(extension, member).getBytes(StandardCharsets.UTF_8);
        }

        private static long packLittleEndian(byte[] value) {
            return packLittleEndian(value, 0, value.length);
        }

        private static long packLittleEndian(byte[] value, int offset, int length) {
            long packed = 0;
            for (int i = 0; i < length; i++) {
                packed |= (long) (value[offset + i] & 0xFF) << (i << 3);
            }
            return packed;
        }

        private static long lowByteMask(int length) {
            return length == Long.BYTES ? -1L : (1L << (length << 3)) - 1;
        }

        private String readerName(RuntimeCodecPlan.StructPlan structure) {
            return "readS" + structureIds.get(structure.schema().id());
        }

        private String readerGroupName(RuntimeCodecPlan.StructPlan structure, int length) {
            return readerName(structure) + "L" + length;
        }

        private String readerAttributeName(RuntimeCodecPlan.StructPlan structure, int chunk) {
            return readerName(structure) + "A" + chunk;
        }

        private static String readerDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + READER + ";L" + Type.getInternalName(structure.builderClass()) + ";)V";
        }

        private static String readerGroupDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + READER + ";L" + Type.getInternalName(structure.builderClass()) + ";)Z";
        }

        private record EnumConstant(Field field, String value) {}

        private record IntEnumConstant(Field field, int value) {}

        // ------------------------------------------------------------------------------------------
        // Values
        // ------------------------------------------------------------------------------------------

        /** Stack on entry: writer, opening run, value. Consumes all three. */
        private void emitElementOnStack(MethodVisitor method, Schema slot, Class<?> javaType, int closeId) {
            Schema target = slot.isMember() ? slot.memberTarget() : slot;
            switch (target.type()) {
                case BOOLEAN -> {
                    unbox(method, javaType, Boolean.class, "booleanValue", "()Z");
                    pushConstant(method, closeId);
                    element(method, "elementBoolean", "([BZ[B)V");
                }
                case BYTE -> {
                    unbox(method, javaType, Byte.class, "byteValue", "()B");
                    pushConstant(method, closeId);
                    element(method, "elementByte", "([BB[B)V");
                }
                case SHORT -> {
                    unbox(method, javaType, Short.class, "shortValue", "()S");
                    pushConstant(method, closeId);
                    element(method, "elementShort", "([BS[B)V");
                }
                case INTEGER -> {
                    unbox(method, javaType, Integer.class, "intValue", "()I");
                    pushConstant(method, closeId);
                    element(method, "elementInteger", "([BI[B)V");
                }
                case LONG -> {
                    unbox(method, javaType, Long.class, "longValue", "()J");
                    pushConstant(method, closeId);
                    element(method, "elementLong", "([BJ[B)V");
                }
                case FLOAT -> {
                    unbox(method, javaType, Float.class, "floatValue", "()F");
                    pushConstant(method, closeId);
                    element(method, "elementFloat", "([BF[B)V");
                }
                case DOUBLE -> {
                    unbox(method, javaType, Double.class, "doubleValue", "()D");
                    pushConstant(method, closeId);
                    element(method, "elementDouble", "([BD[B)V");
                }
                case BIG_INTEGER -> {
                    method.visitTypeInsn(CHECKCAST, "java/math/BigInteger");
                    pushConstant(method, closeId);
                    element(method, "elementBigInteger", "([BLjava/math/BigInteger;[B)V");
                }
                case BIG_DECIMAL -> {
                    method.visitTypeInsn(CHECKCAST, "java/math/BigDecimal");
                    pushConstant(method, closeId);
                    element(method, "elementBigDecimal", "([BLjava/math/BigDecimal;[B)V");
                }
                case STRING -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/String");
                    pushConstant(method, closeId);
                    element(method, "elementString", "([BLjava/lang/String;[B)V");
                }
                case ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                    method.visitMethodInsn(INVOKEINTERFACE, SMITHY_ENUM, "getValue", "()Ljava/lang/String;", true);
                    pushConstant(method, closeId);
                    element(method, "elementString", "([BLjava/lang/String;[B)V");
                }
                case INT_ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_INT_ENUM);
                    method.visitMethodInsn(INVOKEINTERFACE, SMITHY_INT_ENUM, "getValue", "()I", true);
                    pushConstant(method, closeId);
                    element(method, "elementInteger", "([BI[B)V");
                }
                case BLOB -> {
                    method.visitTypeInsn(CHECKCAST, BYTE_BUFFER);
                    pushConstant(method, closeId);
                    element(method, "elementBlob", "([BL" + BYTE_BUFFER + ";[B)V");
                }
                case TIMESTAMP -> {
                    method.visitTypeInsn(CHECKCAST, INSTANT);
                    method.visitLdcInsn(timestampFormat(slot));
                    pushConstant(method, closeId);
                    element(method, "elementTimestamp", "([BL" + INSTANT + ";I[B)V");
                }
                default -> throw new UnsupportedSchemaException(
                        "XML runtime codegen cannot write " + target.type() + " at " + slot.id());
            }
        }

        /** Stack on entry: writer, attribute prefix, value. Consumes all three. */
        private void emitAttributeOnStack(MethodVisitor method, Schema slot, Class<?> javaType) {
            Schema target = slot.isMember() ? slot.memberTarget() : slot;
            switch (target.type()) {
                case STRING -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/String");
                    element(method, "attrString", "([BLjava/lang/String;)V");
                }
                case ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                    method.visitMethodInsn(INVOKEINTERFACE, SMITHY_ENUM, "getValue", "()Ljava/lang/String;", true);
                    element(method, "attrString", "([BLjava/lang/String;)V");
                }
                case BOOLEAN -> {
                    unbox(method, javaType, Boolean.class, "booleanValue", "()Z");
                    element(method, "attrBoolean", "([BZ)V");
                }
                case INTEGER -> {
                    unbox(method, javaType, Integer.class, "intValue", "()I");
                    element(method, "attrInteger", "([BI)V");
                }
                case INT_ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_INT_ENUM);
                    method.visitMethodInsn(INVOKEINTERFACE, SMITHY_INT_ENUM, "getValue", "()I", true);
                    element(method, "attrInteger", "([BI)V");
                }
                case LONG -> {
                    unbox(method, javaType, Long.class, "longValue", "()J");
                    element(method, "attrLong", "([BJ)V");
                }
                case FLOAT -> {
                    unbox(method, javaType, Float.class, "floatValue", "()F");
                    element(method, "attrFloat", "([BF)V");
                }
                case DOUBLE -> {
                    unbox(method, javaType, Double.class, "doubleValue", "()D");
                    element(method, "attrDouble", "([BD)V");
                }
                case TIMESTAMP -> {
                    method.visitTypeInsn(CHECKCAST, INSTANT);
                    method.visitLdcInsn(timestampFormat(slot));
                    element(method, "attrTimestamp", "([BL" + INSTANT + ";I)V");
                }
                default -> throw new UnsupportedSchemaException(
                        "XML runtime codegen cannot write " + target.type() + " as an attribute at " + slot.id());
            }
        }

        private static void element(MethodVisitor method, String name, String descriptor) {
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, name, descriptor, false);
        }

        private static void unbox(
                MethodVisitor method,
                Class<?> actual,
                Class<?> boxed,
                String name,
                String descriptor
        ) {
            if (!actual.isPrimitive()) {
                method.visitTypeInsn(CHECKCAST, Type.getInternalName(boxed));
                method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(boxed), name, descriptor, false);
            }
        }

        private void emitRaw(MethodVisitor method, int writerLocal, int constantId) {
            method.visitVarInsn(ALOAD, writerLocal);
            pushConstant(method, constantId);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "raw", "([B)V", false);
        }

        /**
         * Writes {@code <name></name>}, which is what the interpreted path produces for a null item of
         * a sparse list or a null value of a sparse map: the opening tag is written, {@code writeNull}
         * closes it, and the closing tag follows.
         */
        private void emitEmptyElement(MethodVisitor method, Tags tags) {
            method.visitVarInsn(ALOAD, 2);
            pushConstant(method, constant(tags.open() + ">"));
            pushConstant(method, constant(tags.close()));
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "emptyElement", "([B[B)V", false);
        }

        private void pushConstant(MethodVisitor method, int id) {
            method.visitFieldInsn(GETSTATIC, className, "T" + id, "[B");
        }

        private int constant(String value) {
            Integer existing = constants.get(value);
            if (existing != null) {
                return existing;
            }
            int id = constants.size();
            constants.put(value, id);
            return id;
        }

        // ------------------------------------------------------------------------------------------
        // Name resolution, mirroring SmithyXmlSerializer
        // ------------------------------------------------------------------------------------------

        private Tags memberTags(XmlSchemaExtensions.StructExtension extension, Schema member) {
            return new Tags(memberName(extension, member), memberNamespace(member));
        }

        private static String memberName(XmlSchemaExtensions.StructExtension extension, Schema member) {
            if (extension != null) {
                byte[][] table = extension.nameTable();
                int index = member.memberIndex();
                if (index >= 0 && index < table.length && table[index] != null) {
                    return new String(table[index], StandardCharsets.UTF_8);
                }
            }
            var memberExtension = member.getExtension(XmlSchemaExtensions.KEY);
            if (memberExtension instanceof XmlSchemaExtensions.MemberExtension me) {
                return new String(me.nameBytes(), StandardCharsets.UTF_8);
            }
            return member.memberName();
        }

        /**
         * The name an attribute is <em>found</em> by, which is not always the name it is written with.
         *
         * <p>{@code @xmlName("xsi:someName")} on an attribute member is written verbatim, but the parser
         * records attributes under their local name, dropping any prefix at the first colon. Reading has
         * to drop it too, exactly as {@code readStructContent} does when it falls back to a name lookup.
         */
        private static String attributeLookupName(String name) {
            int colon = name.indexOf(':');
            return colon < 0 ? name : name.substring(colon + 1);
        }

        private static String memberNamespace(Schema member) {
            var memberExtension = member.getExtension(XmlSchemaExtensions.KEY);
            if (memberExtension instanceof XmlSchemaExtensions.MemberExtension me) {
                return me.namespaceBytes() == null
                        ? ""
                        : new String(me.namespaceBytes(), StandardCharsets.UTF_8);
            }
            return directNamespace(member);
        }

        private static String directNamespace(Schema schema) {
            XmlNamespaceTrait namespace = schema.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
            return namespace == null ? "" : XmlRootTags.namespaceRun(namespace);
        }

        private boolean hasAttributes(RuntimeCodecPlan.StructPlan structure) {
            var extension = structExtension(structure.schema());
            if (extension == null) {
                return false;
            }
            // Derived from the members actually in the plan rather than from the schema, so the
            // '>' placement always matches what this class emits even for a narrowed root.
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                if (isAttribute(extension, member.schema())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasElements(
                RuntimeCodecPlan.StructPlan structure,
                XmlSchemaExtensions.StructExtension extension,
                RuntimeCodecPlan.MethodRange range
        ) {
            for (int i = range.startInclusive(); i < range.endExclusive(); i++) {
                if (extension == null || !isAttribute(extension, structure.members().get(i).schema())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isFlattenedMember(XmlSchemaExtensions.StructExtension extension, Schema member) {
            return extension != null && isFlattened(extension, member);
        }

        private static int timestampFormat(Schema schema) {
            TimestampFormatter formatter = TimestampFormatter.of(
                    schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT),
                    TimestampFormatTrait.Format.DATE_TIME);
            return XmlCodegenWriter.formatCode(formatter.format());
        }

        private String writerName(RuntimeCodecPlan.StructPlan structure) {
            return "writeS" + structureIds.get(structure.schema().id());
        }

        private String writerChunkName(RuntimeCodecPlan.StructPlan structure, int chunk) {
            return writerName(structure) + "C" + chunk;
        }

        private static String writerDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + Type.getInternalName(structure.shapeClass()) + ";L" + WRITER + ";)V";
        }

        /** The opening run without its {@code '>'}, and the matching closing run. */
        private record Tags(String name, String namespace) {
            String open() {
                return "<" + name + namespace;
            }

            String close() {
                return "</" + name + ">";
            }
        }

        private record ListKey(ShapeId target, Tags item) {}

        private record ListAggregate(int id, Schema slot, Tags tags) {}

        private record MapKey(
                ShapeId target,
                String entryName,
                String keyName,
                String keyNamespace,
                Tags value) {}

        private record MapAggregate(
                int id,
                Schema slot,
                String entryName,
                String keyName,
                String keyNamespace,
                Tags valueTags) {}
    }
}
