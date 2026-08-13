/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecPlan;
import software.amazon.smithy.java.codecs.commons.internal.codegen.UnsupportedSchemaException;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassWriter;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Label;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.MethodVisitor;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Type;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

final class JsonRuntimeCodegenBackend implements RuntimeCodecBackend<GeneratedJsonCodec>, Opcodes {
    private static final int STRUCTURE_BUILDER_LOCAL = 9;
    private static final String CODEC = Type.getInternalName(GeneratedJsonCodec.class);
    private static final String WRITER = Type.getInternalName(JsonCodegenWriter.class);
    private static final String READER = Type.getInternalName(SmithyJsonDeserializer.class);
    private static final String SETTINGS = Type.getInternalName(JsonSettings.class);
    private static final String SERIALIZABLE_SHAPE = Type.getInternalName(SerializableShape.class);
    private static final String SHAPE_BUILDER = Type.getInternalName(ShapeBuilder.class);
    private static final String DOCUMENT = Type.getInternalName(Document.class);
    private static final String SMITHY_ENUM = Type.getInternalName(SmithyEnum.class);
    private static final String BYTE_BUFFER = Type.getInternalName(ByteBuffer.class);

    private final boolean useJsonName;
    private final boolean forbidUnknownUnionMembers;
    private final JsonSettings settings;

    JsonRuntimeCodegenBackend(JsonSettings settings) {
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
        this.forbidUnknownUnionMembers = settings.forbidUnknownUnionMembers();
    }

    @Override
    public String id() {
        return useJsonName ? "jsonNames" : "jsonMembers";
    }

    @Override
    public Class<GeneratedJsonCodec> codecType() {
        return GeneratedJsonCodec.class;
    }

    @Override
    public Class<?> lookupHost() {
        return GeneratedJsonCodec.class;
    }

    @Override
    public Emission emit(RuntimeCodecPlan plan, String generatedName) {
        validate(plan);
        var generator = new Generator(plan, generatedName, settings, useJsonName, forbidUnknownUnionMembers);
        return generator.generate();
    }

    private static void validate(RuntimeCodecPlan plan) {
        for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
            if (structure.builderFactory() == null && structure.schema() != plan.root()) {
                if (!structure.union()) {
                    throw new UnsupportedSchemaException("No public builder factory for " + structure.schema().id());
                }
            }
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                switch (member.target().type()) {
                    case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL,
                            STRING, ENUM, BLOB, TIMESTAMP, DOCUMENT, LIST, SET, MAP, STRUCTURE, UNION ->
                        {
                        }
                    default -> throw new UnsupportedSchemaException(
                            "JSON runtime codegen does not yet lower " + member.target().type()
                                    + " at " + member.schema().id());
                }
            }
        }
    }

    private static final class Generator {
        private final RuntimeCodecPlan plan;
        private final String className;
        private final JsonSettings settings;
        private final boolean useJsonName;
        private final boolean forbidUnknownUnionMembers;
        private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        private final IdentityHashMap<RuntimeCodecPlan.StructPlan, Integer> structureIds = new IdentityHashMap<>();
        private final IdentityHashMap<RuntimeCodecPlan.MemberPlan, Integer> memberIds = new IdentityHashMap<>();
        private final List<RuntimeCodecPlan.MemberPlan> orderedMembers = new ArrayList<>();
        private final Map<ShapeId, RuntimeCodecPlan.StructPlan> structuresBySchema = new LinkedHashMap<>();
        private final Map<ShapeId, Integer> aggregateIds = new LinkedHashMap<>();
        private final List<Schema> orderedAggregates = new ArrayList<>();
        private final Map<Class<?>, Integer> enumIds = new LinkedHashMap<>();
        private int methodCount;

        Generator(
                RuntimeCodecPlan plan,
                String className,
                JsonSettings settings,
                boolean useJsonName,
                boolean forbidUnknownUnionMembers
        ) {
            this.plan = plan;
            this.className = className;
            this.settings = settings;
            this.useJsonName = useJsonName;
            this.forbidUnknownUnionMembers = forbidUnknownUnionMembers;
            for (int i = 0; i < plan.structures().size(); i++) {
                RuntimeCodecPlan.StructPlan structure = plan.structures().get(i);
                structureIds.put(structure, i);
                structuresBySchema.put(structure.schema().id(), structure);
                for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                    memberIds.put(member, memberIds.size());
                    orderedMembers.add(member);
                    collectTarget(member.target());
                }
            }
        }

        private void collectTarget(Schema schema) {
            schema = schema.isMember() ? schema.memberTarget() : schema;
            switch (schema.type()) {
                case LIST, SET -> {
                    ShapeId key = schema.id();
                    if (!aggregateIds.containsKey(key)) {
                        aggregateIds.put(key, aggregateIds.size());
                        orderedAggregates.add(schema);
                        collectTarget(schema.listMember());
                    }
                }
                case MAP -> {
                    ShapeId key = schema.id();
                    if (!aggregateIds.containsKey(key)) {
                        aggregateIds.put(key, aggregateIds.size());
                        orderedAggregates.add(schema);
                        collectTarget(schema.mapValueMember());
                    }
                }
                case ENUM -> enumIds.computeIfAbsent(schema.shapeClass(), ignored -> enumIds.size());
                default -> {
                }
            }
        }

        Emission generate() {
            writer.visit(V17, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Object", new String[] {CODEC});
            emitFields();
            emitConstructor();
            emitClassInitializer();
            emitEnumReaders();
            emitAggregateMethods();
            for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
                emitWriter(structure);
                emitReader(structure);
            }
            emitWriteEntry();
            emitReadEntry();
            emitScanEntry();
            writer.visitEnd();
            return new Emission(writer.toByteArray(), methodCount);
        }

        private void emitFields() {
            for (RuntimeCodecPlan.MemberPlan member : orderedMembers) {
                int id = memberIds.get(member);
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "N" + id, "[B", null, null).visitEnd();
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "W" + id, "[B", null, null).visitEnd();
            }
        }

        private void emitConstructor() {
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 0);
            method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitClassInitializer() {
            MethodVisitor method = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            method.visitCode();
            for (RuntimeCodecPlan.MemberPlan member : orderedMembers) {
                int id = memberIds.get(member);
                emitUtf8(method, member.wireName(useJsonName));
                method.visitFieldInsn(PUTSTATIC, className, "N" + id, "[B");
                var extension = member.schema().getExtension(SmithyJsonSchemaExtensions.KEY);
                byte[] fieldToken = useJsonName
                        ? extension.jsonNameBytes()
                        : extension.memberNameBytes();
                emitUtf8(method, new String(fieldToken, StandardCharsets.UTF_8));
                method.visitFieldInsn(PUTSTATIC, className, "W" + id, "[B");
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitEnumReaders() {
            for (Map.Entry<Class<?>, Integer> entry : enumIds.entrySet()) {
                Class<?> enumClass = entry.getKey();
                Method from;
                Method unknown;
                try {
                    from = enumClass.getMethod("from", String.class);
                    unknown = enumClass.getMethod("unknown", String.class);
                } catch (NoSuchMethodException e) {
                    throw new UnsupportedSchemaException("Generated enum lacks from/unknown methods: "
                            + enumClass.getName());
                }
                MethodVisitor method = writer.visitMethod(
                        ACC_PRIVATE,
                        enumReaderName(enumClass),
                        "(L" + READER + ";)L" + Type.getInternalName(enumClass) + ";",
                        null,
                        null);
                Label start = new Label();
                Label end = new Label();
                Label handler = new Label();
                method.visitTryCatchBlock(start, end, handler, "java/lang/IllegalArgumentException");
                method.visitCode();
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        "generatedReadString",
                        "()Ljava/lang/String;",
                        false);
                method.visitVarInsn(ASTORE, 2);
                method.visitLabel(start);
                method.visitVarInsn(ALOAD, 2);
                invoke(method, from);
                method.visitLabel(end);
                method.visitInsn(ARETURN);
                method.visitLabel(handler);
                method.visitInsn(POP);
                method.visitVarInsn(ALOAD, 2);
                invoke(method, unknown);
                method.visitInsn(ARETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                methodCount++;
            }
        }

        private void emitAggregateMethods() {
            for (var schema : orderedAggregates) {
                if (schema.type() == ShapeType.MAP) {
                    emitMapWriter(schema);
                    emitMapReader(schema);
                } else {
                    emitListWriter(schema);
                    emitListReader(schema);
                }
            }
        }

        private void emitListWriter(Schema schema) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    aggregateWriterName(schema),
                    "(Ljava/util/List;L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "beginArray", "()V", false);
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
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/List",
                    "get",
                    "(I)Ljava/lang/Object;",
                    true);
            method.visitVarInsn(ASTORE, 5);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ILOAD, 3);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "element", "(I)V", false);
            Label nonNull = new Label();
            Label next = new Label();
            method.visitVarInsn(ALOAD, 5);
            method.visitJumpInsn(IFNONNULL, nonNull);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeNull", "()V", false);
            method.visitJumpInsn(GOTO, next);
            method.visitLabel(nonNull);
            emitWriteTarget(method, schema.listMember(), 5, 2);
            method.visitLabel(next);
            method.visitIincInsn(3, 1);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "endArray", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitListReader(Schema schema) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    aggregateReaderName(schema),
                    "(L" + READER + ";)Ljava/util/List;",
                    null,
                    null);
            method.visitCode();
            method.visitInsn(ACONST_NULL);
            method.visitVarInsn(ASTORE, 2);
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ISTORE, 3);
            for (int local = 5; local <= 8; local++) {
                method.visitInsn(ACONST_NULL);
                method.visitVarInsn(ASTORE, local);
            }
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginArray", "()Z", false);
            Label done = new Label();
            Label loop = new Label();
            method.visitJumpInsn(IFEQ, done);
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
            Label value = new Label();
            Label decoded = new Label();
            method.visitJumpInsn(IFEQ, value);
            method.visitInsn(ACONST_NULL);
            method.visitJumpInsn(GOTO, decoded);
            method.visitLabel(value);
            emitReadTarget(method, schema.listMember(), 1);
            method.visitLabel(decoded);
            method.visitVarInsn(ASTORE, 4);

            Label append = new Label();
            Label added = new Label();
            method.visitVarInsn(ALOAD, 2);
            method.visitJumpInsn(IFNONNULL, append);
            Label spill = new Label();
            Label[] slots = {new Label(), new Label(), new Label(), new Label()};
            method.visitVarInsn(ILOAD, 3);
            method.visitLookupSwitchInsn(spill, new int[] {0, 1, 2, 3}, slots);
            for (int i = 0; i < slots.length; i++) {
                method.visitLabel(slots[i]);
                method.visitVarInsn(ALOAD, 4);
                method.visitVarInsn(ASTORE, 5 + i);
                method.visitJumpInsn(GOTO, added);
            }
            method.visitLabel(spill);
            emitNewArrayList(method, 8);
            method.visitVarInsn(ASTORE, 2);
            for (int local = 5; local <= 8; local++) {
                emitArrayListAdd(method, 2, local);
            }
            emitArrayListAdd(method, 2, 4);
            method.visitJumpInsn(GOTO, added);

            method.visitLabel(append);
            emitArrayListAdd(method, 2, 4);
            method.visitLabel(added);
            method.visitIincInsn(3, 1);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedArrayHasNext", "()Z", false);
            method.visitJumpInsn(IFNE, loop);
            method.visitLabel(done);

            Label result = new Label();
            method.visitVarInsn(ALOAD, 2);
            method.visitJumpInsn(IFNONNULL, result);
            method.visitTypeInsn(NEW, "java/util/ArrayList");
            method.visitInsn(DUP);
            method.visitVarInsn(ILOAD, 3);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    "java/util/ArrayList",
                    "<init>",
                    "(I)V",
                    false);
            method.visitVarInsn(ASTORE, 2);
            Label impossible = new Label();
            Label[] sizes = {new Label(), new Label(), new Label(), new Label(), new Label()};
            method.visitVarInsn(ILOAD, 3);
            method.visitLookupSwitchInsn(impossible, new int[] {0, 1, 2, 3, 4}, sizes);
            for (int size = 0; size < sizes.length; size++) {
                method.visitLabel(sizes[size]);
                for (int local = 5; local < 5 + size; local++) {
                    emitArrayListAdd(method, 2, local);
                }
                method.visitJumpInsn(GOTO, result);
            }
            method.visitLabel(impossible);
            emitThrow(method, "Invalid staged list size");
            method.visitLabel(result);
            method.visitVarInsn(ALOAD, 2);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private static void emitNewArrayList(MethodVisitor method, int capacity) {
            method.visitTypeInsn(NEW, "java/util/ArrayList");
            method.visitInsn(DUP);
            method.visitLdcInsn(capacity);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    "java/util/ArrayList",
                    "<init>",
                    "(I)V",
                    false);
        }

        private static void emitArrayListAdd(
                MethodVisitor method,
                int listLocal,
                int valueLocal
        ) {
            method.visitVarInsn(ALOAD, listLocal);
            method.visitVarInsn(ALOAD, valueLocal);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/util/ArrayList",
                    "add",
                    "(Ljava/lang/Object;)Z",
                    false);
            method.visitInsn(POP);
        }

        private void emitMapWriter(Schema schema) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    aggregateWriterName(schema),
                    "(Ljava/util/Map;L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "beginObject", "()V", false);
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ISTORE, 6);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "entrySet", "()Ljava/util/Set;", true);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Set",
                    "iterator",
                    "()Ljava/util/Iterator;",
                    true);
            method.visitVarInsn(ASTORE, 3);
            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
            method.visitJumpInsn(IFEQ, done);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Iterator",
                    "next",
                    "()Ljava/lang/Object;",
                    true);
            method.visitTypeInsn(CHECKCAST, "java/util/Map$Entry");
            method.visitVarInsn(ASTORE, 4);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map$Entry",
                    "getKey",
                    "()Ljava/lang/Object;",
                    true);
            method.visitTypeInsn(CHECKCAST, "java/lang/String");
            method.visitVarInsn(ILOAD, 6);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    WRITER,
                    "dynamicField",
                    "(Ljava/lang/String;I)V",
                    false);
            method.visitIincInsn(6, 1);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map$Entry",
                    "getValue",
                    "()Ljava/lang/Object;",
                    true);
            method.visitVarInsn(ASTORE, 5);
            Label nonNull = new Label();
            Label next = new Label();
            method.visitVarInsn(ALOAD, 5);
            method.visitJumpInsn(IFNONNULL, nonNull);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeNull", "()V", false);
            method.visitJumpInsn(GOTO, next);
            method.visitLabel(nonNull);
            emitWriteTarget(method, schema.mapValueMember(), 5, 2);
            method.visitLabel(next);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "endObject", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitMapReader(Schema schema) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    aggregateReaderName(schema),
                    "(L" + READER + ";)Ljava/util/Map;",
                    null,
                    null);
            method.visitCode();
            method.visitTypeInsn(NEW, "java/util/LinkedHashMap");
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
            method.visitVarInsn(ASTORE, 2);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginObject", "()Z", false);
            Label done = new Label();
            Label loop = new Label();
            method.visitJumpInsn(IFEQ, done);
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    "generatedReadMapKey",
                    "()Ljava/lang/String;",
                    false);
            method.visitVarInsn(ASTORE, 3);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 3);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
            Label value = new Label();
            Label put = new Label();
            method.visitJumpInsn(IFEQ, value);
            method.visitInsn(ACONST_NULL);
            method.visitJumpInsn(GOTO, put);
            method.visitLabel(value);
            emitReadTarget(method, schema.mapValueMember(), 1);
            method.visitLabel(put);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map",
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    true);
            method.visitInsn(POP);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedObjectHasNext", "()Z", false);
            method.visitJumpInsn(IFNE, loop);
            method.visitLabel(done);
            method.visitVarInsn(ALOAD, 2);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private static void emitUtf8(MethodVisitor method, String value) {
            method.visitLdcInsn(value);
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
        }

        private void emitWriteEntry() {
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "write",
                    "(L" + SERIALIZABLE_SHAPE + ";L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            RuntimeCodecPlan.StructPlan root = plan.rootStructure();
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
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitReadEntry() {
            RuntimeCodecPlan.StructPlan root = plan.rootStructure();
            String builderName = Type.getInternalName(root.builderClass());
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "read",
                    "([BL" + SHAPE_BUILDER + ";L" + SETTINGS + ";)L" + SERIALIZABLE_SHAPE + ";",
                    null,
                    null);
            method.visitCode();
            method.visitTypeInsn(NEW, READER);
            method.visitInsn(DUP);
            method.visitVarInsn(ALOAD, 1);
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ALOAD, 1);
            method.visitInsn(ARRAYLENGTH);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    READER,
                    "<init>",
                    "([BIIL" + SETTINGS + ";)V",
                    false);
            method.visitVarInsn(ASTORE, 4);

            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 4);
            method.visitVarInsn(ALOAD, 2);
            method.visitTypeInsn(CHECKCAST, builderName);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    readerName(root),
                    readerDescriptor(root),
                    false);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "close", "()V", false);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    SHAPE_BUILDER,
                    "errorCorrection",
                    "()L" + SHAPE_BUILDER + ";",
                    true);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    SHAPE_BUILDER,
                    "build",
                    "()L" + Type.getInternalName(software.amazon.smithy.java.core.schema.SerializableShape.class) + ";",
                    true);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitScanEntry() {
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "scan",
                    "([BL" + SETTINGS + ";)I",
                    null,
                    null);
            method.visitCode();
            method.visitTypeInsn(NEW, READER);
            method.visitInsn(DUP);
            method.visitVarInsn(ALOAD, 1);
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ALOAD, 1);
            method.visitInsn(ARRAYLENGTH);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    READER,
                    "<init>",
                    "([BIIL" + SETTINGS + ";)V",
                    false);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedScan", "()I", false);
            method.visitInsn(IRETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWriter(RuntimeCodecPlan.StructPlan structure) {
            if (structure.union()) {
                emitUnionWriter(structure);
                return;
            }
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    writerName(structure),
                    writerDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "beginObject", "()V", false);
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ISTORE, 3);
            List<RuntimeCodecPlan.MemberPlan> members = structure.members();
            List<RuntimeCodecPlan.MethodRange> chunks = structure.writerChunks();
            for (int chunk = 0; chunk < chunks.size(); chunk++) {
                method.visitVarInsn(ALOAD, 0);
                method.visitVarInsn(ALOAD, 1);
                method.visitVarInsn(ALOAD, 2);
                method.visitVarInsn(ILOAD, 3);
                method.visitMethodInsn(
                        INVOKESPECIAL,
                        className,
                        writerChunkName(structure, chunk),
                        writerChunkDescriptor(structure),
                        false);
                method.visitVarInsn(ISTORE, 3);
            }
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "endObject", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;

            for (int chunk = 0; chunk < chunks.size(); chunk++) {
                RuntimeCodecPlan.MethodRange range = chunks.get(chunk);
                MethodVisitor chunkMethod = writer.visitMethod(
                        ACC_PRIVATE,
                        writerChunkName(structure, chunk),
                        writerChunkDescriptor(structure),
                        null,
                        null);
                chunkMethod.visitCode();
                for (int i = range.startInclusive(); i < range.endExclusive(); i++) {
                    emitWriteMember(chunkMethod, members.get(i), 3, 4);
                }
                chunkMethod.visitVarInsn(ILOAD, 3);
                chunkMethod.visitInsn(IRETURN);
                chunkMethod.visitMaxs(0, 0);
                chunkMethod.visitEnd();
                methodCount++;
            }
        }

        private void emitUnionWriter(RuntimeCodecPlan.StructPlan structure) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    writerName(structure),
                    writerDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "beginObject", "()V", false);
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                Label next = new Label();
                method.visitVarInsn(ALOAD, 1);
                method.visitTypeInsn(INSTANCEOF, Type.getInternalName(member.unionVariant()));
                method.visitJumpInsn(IFEQ, next);
                emitField(method, member, -1);
                Method accessor = member.unionAccessor();
                Class<?> valueType = accessor.getReturnType();
                if (valueType.isPrimitive()) {
                    method.visitVarInsn(ALOAD, 2);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(member.unionVariant()));
                    invoke(method, accessor);
                    emitWriteTargetOnStack(method, member.schema(), valueType);
                } else {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(member.unionVariant()));
                    invoke(method, accessor);
                    method.visitVarInsn(ASTORE, 3);
                    emitWriteTarget(method, member.schema(), 3, 2);
                }
                method.visitVarInsn(ALOAD, 2);
                method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "endObject", "()V", false);
                method.visitInsn(RETURN);
                method.visitLabel(next);
            }
            emitThrow(method, "Unsupported or unknown union variant for " + structure.schema().id());
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWriteMember(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                int indexLocal,
                int valueLocal
        ) {
            Label skip = new Label();
            Method presence = member.presence();
            if (presence != null) {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, presence);
                method.visitJumpInsn(IFEQ, skip);
            }

            Class<?> returnType = member.getter().getReturnType();
            if (!returnType.isPrimitive()) {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.getter());
                method.visitVarInsn(ASTORE, valueLocal);
                method.visitVarInsn(ALOAD, valueLocal);
                method.visitJumpInsn(IFNULL, skip);
                if (member.target().type() == ShapeType.STRING) {
                    emitStringField(method, member, indexLocal, valueLocal);
                    method.visitLabel(skip);
                    return;
                }
                emitField(method, member, indexLocal);
                if (member.target().type() == ShapeType.STRUCTURE
                        || member.target().type() == ShapeType.UNION) {
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(member.target().id());
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            writerName(nested),
                            writerDescriptor(nested),
                            false);
                } else if (member.target().type() == ShapeType.LIST
                        || member.target().type() == ShapeType.SET) {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, "java/util/List");
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateWriterName(member.target()),
                            "(Ljava/util/List;L" + WRITER + ";)V",
                            false);
                } else if (member.target().type() == ShapeType.MAP) {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, "java/util/Map");
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateWriterName(member.target()),
                            "(Ljava/util/Map;L" + WRITER + ";)V",
                            false);
                } else {
                    method.visitVarInsn(ALOAD, 2);
                    method.visitVarInsn(ALOAD, valueLocal);
                    emitWriteValue(method, member, returnType);
                }
                method.visitLabel(skip);
                return;
            }
            emitField(method, member, indexLocal);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 1);
            invoke(method, member.getter());
            emitWriteValue(method, member, returnType);
            method.visitLabel(skip);
        }

        private void emitStringField(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                int indexLocal,
                int valueLocal
        ) {
            method.visitVarInsn(ALOAD, 2);
            method.visitFieldInsn(GETSTATIC, className, "W" + memberIds.get(member), "[B");
            method.visitVarInsn(ILOAD, indexLocal);
            method.visitVarInsn(ALOAD, valueLocal);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    WRITER,
                    "fieldString",
                    "([BILjava/lang/String;)V",
                    false);
            method.visitIincInsn(indexLocal, 1);
        }

        private void emitField(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                int indexLocal
        ) {
            method.visitVarInsn(ALOAD, 2);
            method.visitFieldInsn(GETSTATIC, className, "W" + memberIds.get(member), "[B");
            if (indexLocal < 0) {
                method.visitInsn(ICONST_0);
            } else {
                method.visitVarInsn(ILOAD, indexLocal);
            }
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "field", "([BI)V", false);
            if (indexLocal >= 0) {
                method.visitIincInsn(indexLocal, 1);
            }
        }

        private void emitWriteValue(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                Class<?> javaType
        ) {
            ShapeType type = member.target().type();
            String descriptor;
            switch (type) {
                case BOOLEAN -> {
                    unbox(method, javaType, Boolean.class, "booleanValue", "()Z");
                    descriptor = "(Z)V";
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeBoolean", descriptor, false);
                }
                case BYTE -> {
                    unbox(method, javaType, Byte.class, "byteValue", "()B");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeByte", "(B)V", false);
                }
                case SHORT -> {
                    unbox(method, javaType, Short.class, "shortValue", "()S");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeShort", "(S)V", false);
                }
                case INTEGER -> {
                    unbox(method, javaType, Integer.class, "intValue", "()I");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeInteger", "(I)V", false);
                }
                case LONG -> {
                    unbox(method, javaType, Long.class, "longValue", "()J");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeLong", "(J)V", false);
                }
                case FLOAT -> {
                    unbox(method, javaType, Float.class, "floatValue", "()F");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeFloat", "(F)V", false);
                }
                case DOUBLE -> {
                    unbox(method, javaType, Double.class, "doubleValue", "()D");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeDouble", "(D)V", false);
                }
                case BIG_INTEGER -> method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        WRITER,
                        "writeBigInteger",
                        "(Ljava/math/BigInteger;)V",
                        false);
                case BIG_DECIMAL -> method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        WRITER,
                        "writeBigDecimal",
                        "(Ljava/math/BigDecimal;)V",
                        false);
                case STRING -> method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        WRITER,
                        "writeString",
                        "(Ljava/lang/String;)V",
                        false);
                case BLOB -> method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        WRITER,
                        "writeBlob",
                        "(L" + BYTE_BUFFER + ";)V",
                        false);
                case TIMESTAMP -> {
                    method.visitLdcInsn(timestampFormat(member));
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeTimestamp",
                            "(L" + Type.getInternalName(Instant.class) + ";I)V",
                            false);
                }
                case DOCUMENT -> method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        WRITER,
                        "writeDocument",
                        "(L" + DOCUMENT + ";)V",
                        false);
                case ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                    method.visitMethodInsn(
                            INVOKEINTERFACE,
                            SMITHY_ENUM,
                            "getValue",
                            "()Ljava/lang/String;",
                            true);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeString",
                            "(Ljava/lang/String;)V",
                            false);
                }
                case LIST, SET, MAP, UNION -> throw new AssertionError("Aggregate writes are emitted by the caller");
                case STRUCTURE -> throw new AssertionError("Structure writes are emitted by the caller");
                default -> throw new AssertionError(type);
            }
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

        private void emitWriteTarget(
                MethodVisitor method,
                Schema schema,
                int valueLocal,
                int writerLocal
        ) {
            var target = schema.isMember() ? schema.memberTarget() : schema;
            switch (target.type()) {
                case STRUCTURE, UNION -> {
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(target.id());
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(nested.shapeClass()));
                    method.visitVarInsn(ALOAD, writerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            writerName(nested),
                            writerDescriptor(nested),
                            false);
                }
                case LIST, SET -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, "java/util/List");
                    method.visitVarInsn(ALOAD, writerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateWriterName(target),
                            "(Ljava/util/List;L" + WRITER + ";)V",
                            false);
                }
                case MAP -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, "java/util/Map");
                    method.visitVarInsn(ALOAD, writerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateWriterName(target),
                            "(Ljava/util/Map;L" + WRITER + ";)V",
                            false);
                }
                default -> {
                    method.visitVarInsn(ALOAD, writerLocal);
                    method.visitVarInsn(ALOAD, valueLocal);
                    emitWriteTargetOnStack(method, schema, Object.class);
                }
            }
        }

        private void emitWriteTargetOnStack(
                MethodVisitor method,
                Schema schema,
                Class<?> javaType
        ) {
            var target = schema.isMember() ? schema.memberTarget() : schema;
            switch (target.type()) {
                case BOOLEAN -> {
                    castAndUnbox(method, javaType, Boolean.class, "booleanValue", "()Z");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeBoolean", "(Z)V", false);
                }
                case BYTE -> {
                    castAndUnbox(method, javaType, Byte.class, "byteValue", "()B");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeByte", "(B)V", false);
                }
                case SHORT -> {
                    castAndUnbox(method, javaType, Short.class, "shortValue", "()S");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeShort", "(S)V", false);
                }
                case INTEGER -> {
                    castAndUnbox(method, javaType, Integer.class, "intValue", "()I");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeInteger", "(I)V", false);
                }
                case LONG -> {
                    castAndUnbox(method, javaType, Long.class, "longValue", "()J");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeLong", "(J)V", false);
                }
                case FLOAT -> {
                    castAndUnbox(method, javaType, Float.class, "floatValue", "()F");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeFloat", "(F)V", false);
                }
                case DOUBLE -> {
                    castAndUnbox(method, javaType, Double.class, "doubleValue", "()D");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "writeDouble", "(D)V", false);
                }
                case BIG_INTEGER -> {
                    method.visitTypeInsn(CHECKCAST, "java/math/BigInteger");
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeBigInteger",
                            "(Ljava/math/BigInteger;)V",
                            false);
                }
                case BIG_DECIMAL -> {
                    method.visitTypeInsn(CHECKCAST, "java/math/BigDecimal");
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeBigDecimal",
                            "(Ljava/math/BigDecimal;)V",
                            false);
                }
                case STRING -> {
                    method.visitTypeInsn(CHECKCAST, "java/lang/String");
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeString",
                            "(Ljava/lang/String;)V",
                            false);
                }
                case ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                    method.visitMethodInsn(
                            INVOKEINTERFACE,
                            SMITHY_ENUM,
                            "getValue",
                            "()Ljava/lang/String;",
                            true);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeString",
                            "(Ljava/lang/String;)V",
                            false);
                }
                case BLOB -> {
                    method.visitTypeInsn(CHECKCAST, BYTE_BUFFER);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeBlob",
                            "(L" + BYTE_BUFFER + ";)V",
                            false);
                }
                case TIMESTAMP -> {
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(Instant.class));
                    method.visitLdcInsn(timestampFormat(schema));
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeTimestamp",
                            "(L" + Type.getInternalName(Instant.class) + ";I)V",
                            false);
                }
                case DOCUMENT -> {
                    method.visitTypeInsn(CHECKCAST, DOCUMENT);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            WRITER,
                            "writeDocument",
                            "(L" + DOCUMENT + ";)V",
                            false);
                }
                default -> throw new AssertionError(target.type());
            }
        }

        private static void castAndUnbox(
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

        private void emitReader(RuntimeCodecPlan.StructPlan structure) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    readerName(structure),
                    readerDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginObject", "()Z", false);
            Label done = new Label();
            method.visitJumpInsn(IFEQ, done);
            Label loop = new Label();
            if (canFuseOrderedObjectFraming(structure)) {
                Label fallback = new Label();
                RuntimeCodecPlan.MemberPlan first = structure.members().getFirst();
                emitTryReadField(method, first, false);
                method.visitJumpInsn(IFEQ, fallback);
                emitReadMember(method, first);
                for (int i = 1; i < structure.members().size(); i++) {
                    RuntimeCodecPlan.MemberPlan member = structure.members().get(i);
                    Label next = new Label();
                    emitTryReadField(method, member, true);
                    method.visitJumpInsn(IFEQ, next);
                    emitReadMember(method, member);
                    method.visitLabel(next);
                }
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        "generatedObjectHasNext",
                        "()Z",
                        false);
                method.visitJumpInsn(IFEQ, done);
                method.visitLabel(fallback);
            } else {
                for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                    Label next = new Label();
                    emitTryReadField(method, member, false);
                    method.visitJumpInsn(IFEQ, next);
                    emitReadMember(method, member);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedObjectHasNext",
                            "()Z",
                            false);
                    method.visitJumpInsn(IFEQ, done);
                    method.visitLabel(next);
                }
            }
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedReadFieldHash", "()I", false);
            method.visitVarInsn(ISTORE, 3);
            int buckets = readerBucketCount(structure);
            if (buckets == 1) {
                emitReadDispatch(method, structure);
            } else {
                Label dispatched = new Label();
                Label unknown = new Label();
                Map<Integer, Integer> hashBuckets = new LinkedHashMap<>();
                for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                    int hash = fieldHash(member.wireName(useJsonName));
                    hashBuckets.put(hash, Math.floorMod(hash, buckets));
                }
                List<Integer> hashes = hashBuckets.keySet().stream().sorted().toList();
                int[] switchKeys = hashes.stream().mapToInt(Integer::intValue).toArray();
                Label[] bucketLabels = new Label[buckets];
                for (int bucket = 0; bucket < buckets; bucket++) {
                    bucketLabels[bucket] = new Label();
                }
                Label[] switchLabels = new Label[hashes.size()];
                for (int i = 0; i < hashes.size(); i++) {
                    switchLabels[i] = bucketLabels[hashBuckets.get(hashes.get(i))];
                }
                method.visitVarInsn(ILOAD, 3);
                method.visitLookupSwitchInsn(unknown, switchKeys, switchLabels);
                for (int bucket = 0; bucket < buckets; bucket++) {
                    method.visitLabel(bucketLabels[bucket]);
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitVarInsn(ALOAD, 2);
                    method.visitVarInsn(ILOAD, 3);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            readerBucketName(structure, bucket),
                            readerBucketDescriptor(structure),
                            false);
                    method.visitJumpInsn(IFEQ, unknown);
                    method.visitJumpInsn(GOTO, dispatched);
                }
                method.visitLabel(unknown);
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipValue", "()V", false);
                method.visitLabel(dispatched);
            }
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedObjectHasNext", "()Z", false);
            method.visitJumpInsn(IFNE, loop);
            method.visitLabel(done);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
            if (buckets > 1) {
                for (int bucket = 0; bucket < buckets; bucket++) {
                    emitReaderBucket(structure, bucket, buckets);
                }
            }
            if (structure.union()) {
                emitUnionValueReader(structure);
            }
        }

        private boolean canFuseOrderedObjectFraming(RuntimeCodecPlan.StructPlan structure) {
            return !structure.union()
                    && !structure.members().isEmpty()
                    && structure.members().getFirst().required();
        }

        private void emitUnionValueReader(RuntimeCodecPlan.StructPlan structure) {
            String unionName = Type.getInternalName(structure.shapeClass());
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    unionValueReaderName(structure),
                    "(L" + READER + ";)L" + unionName + ";",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginObject", "()Z", false);
            Label nonEmpty = new Label();
            method.visitJumpInsn(IFNE, nonEmpty);
            emitThrow(method, "Union object must contain one member");
            method.visitLabel(nonEmpty);
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                Label next = new Label();
                emitTryReadField(method, member);
                method.visitJumpInsn(IFEQ, next);
                emitUnionValue(method, member);
                method.visitLabel(next);
            }
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedReadFieldHash", "()I", false);
            method.visitVarInsn(ISTORE, 2);

            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new LinkedHashMap<>();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                groups.computeIfAbsent(fieldHash(member.wireName(useJsonName)), ignored -> new ArrayList<>())
                        .add(member);
            }
            List<Integer> keys = groups.keySet().stream().sorted().toList();
            int[] switchKeys = keys.stream().mapToInt(Integer::intValue).toArray();
            Label unknown = new Label();
            Label[] labels = keys.stream().map(ignored -> new Label()).toArray(Label[]::new);
            method.visitVarInsn(ILOAD, 2);
            method.visitLookupSwitchInsn(unknown, switchKeys, labels);
            for (int i = 0; i < keys.size(); i++) {
                method.visitLabel(labels[i]);
                for (RuntimeCodecPlan.MemberPlan member : groups.get(keys.get(i))) {
                    Label next = new Label();
                    method.visitVarInsn(ALOAD, 1);
                    method.visitFieldInsn(GETSTATIC, className, "N" + memberIds.get(member), "[B");
                    method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedFieldEquals", "([B)Z", false);
                    method.visitJumpInsn(IFEQ, next);
                    emitUnionValue(method, member);
                    method.visitLabel(next);
                }
                method.visitJumpInsn(GOTO, unknown);
            }

            method.visitLabel(unknown);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    "generatedFieldName",
                    "()Ljava/lang/String;",
                    false);
            method.visitVarInsn(ASTORE, 3);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipValue", "()V", false);
            emitRequireUnionEnd(method);
            if (forbidUnknownUnionMembers) {
                emitThrow(method, "Unknown union member");
            } else {
                Class<?> unknownClass = findUnknownUnionClass(structure.shapeClass());
                method.visitTypeInsn(NEW, Type.getInternalName(unknownClass));
                method.visitInsn(DUP);
                method.visitVarInsn(ALOAD, 3);
                method.visitMethodInsn(
                        INVOKESPECIAL,
                        Type.getInternalName(unknownClass),
                        "<init>",
                        "(Ljava/lang/String;)V",
                        false);
                method.visitInsn(ARETURN);
            }
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitUnionValue(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member
        ) {
            String variant = Type.getInternalName(member.unionVariant());
            method.visitTypeInsn(NEW, variant);
            method.visitInsn(DUP);
            emitReadTarget(method, member.schema(), 1);
            Class<?> parameter = member.unionAccessor().getReturnType();
            unboxIfPrimitive(method, parameter);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    variant,
                    "<init>",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(parameter)),
                    false);
            method.visitVarInsn(ASTORE, 3);
            emitRequireUnionEnd(method);
            method.visitVarInsn(ALOAD, 3);
            method.visitInsn(ARETURN);
        }

        private void emitRequireUnionEnd(MethodVisitor method) {
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedObjectHasNext", "()Z", false);
            Label ended = new Label();
            method.visitJumpInsn(IFEQ, ended);
            emitThrow(method, "Union object contains multiple members");
            method.visitLabel(ended);
        }

        private static Class<?> findUnknownUnionClass(Class<?> unionClass) {
            for (Class<?> nested : unionClass.getDeclaredClasses()) {
                if (nested.getSimpleName().equals("$Unknown")) {
                    return nested;
                }
            }
            throw new UnsupportedSchemaException("No unknown union variant on " + unionClass.getName());
        }

        private static void unboxIfPrimitive(MethodVisitor method, Class<?> type) {
            if (!type.isPrimitive()) {
                return;
            }
            if (type == boolean.class) {
                method.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            } else if (type == byte.class) {
                method.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
            } else if (type == short.class) {
                method.visitTypeInsn(CHECKCAST, "java/lang/Short");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
            } else if (type == int.class) {
                method.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            } else if (type == long.class) {
                method.visitTypeInsn(CHECKCAST, "java/lang/Long");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            } else if (type == float.class) {
                method.visitTypeInsn(CHECKCAST, "java/lang/Float");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            } else if (type == double.class) {
                method.visitTypeInsn(CHECKCAST, "java/lang/Double");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            }
        }

        private void emitReadDispatch(MethodVisitor method, RuntimeCodecPlan.StructPlan structure) {
            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new LinkedHashMap<>();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                groups.computeIfAbsent(fieldHash(member.wireName(useJsonName)), ignored -> new ArrayList<>())
                        .add(member);
            }
            List<Integer> keys = groups.keySet().stream().sorted(Comparator.naturalOrder()).toList();
            int[] switchKeys = keys.stream().mapToInt(Integer::intValue).toArray();
            Label unknown = new Label();
            Label after = new Label();
            Label[] labels = keys.stream().map(ignored -> new Label()).toArray(Label[]::new);
            method.visitVarInsn(ILOAD, 3);
            method.visitLookupSwitchInsn(unknown, switchKeys, labels);
            for (int i = 0; i < keys.size(); i++) {
                method.visitLabel(labels[i]);
                List<RuntimeCodecPlan.MemberPlan> collisions = groups.get(keys.get(i));
                for (RuntimeCodecPlan.MemberPlan member : collisions) {
                    Label next = new Label();
                    method.visitVarInsn(ALOAD, 1);
                    method.visitFieldInsn(GETSTATIC, className, "N" + memberIds.get(member), "[B");
                    method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedFieldEquals", "([B)Z", false);
                    method.visitJumpInsn(IFEQ, next);
                    emitReadMember(method, member);
                    method.visitJumpInsn(GOTO, after);
                    method.visitLabel(next);
                }
                method.visitJumpInsn(GOTO, unknown);
            }
            method.visitLabel(unknown);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipValue", "()V", false);
            method.visitLabel(after);
        }

        private void emitTryReadField(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            emitTryReadField(method, member, false);
        }

        private void emitTryReadField(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                boolean afterValue
        ) {
            byte[] token = fieldToken(member);
            method.visitVarInsn(ALOAD, 1);
            if (token.length <= Long.BYTES) {
                long mask = lowByteMask(token.length);
                method.visitLdcInsn(packLittleEndian(token, 0, token.length) & mask);
                method.visitLdcInsn(mask);
                method.visitLdcInsn(token.length);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        afterValue ? "generatedTryReadNextField8" : "generatedTryReadField8",
                        "(JJI)Z",
                        false);
            } else if (token.length <= Long.BYTES * 2) {
                int suffixLength = token.length - Long.BYTES;
                long suffixMask = lowByteMask(suffixLength);
                method.visitLdcInsn(packLittleEndian(token, 0, Long.BYTES));
                method.visitLdcInsn(packLittleEndian(token, Long.BYTES, suffixLength) & suffixMask);
                method.visitLdcInsn(suffixMask);
                method.visitLdcInsn(token.length);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        afterValue ? "generatedTryReadNextField16" : "generatedTryReadField16",
                        "(JJJI)Z",
                        false);
            } else {
                method.visitFieldInsn(GETSTATIC, className, "W" + memberIds.get(member), "[B");
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        afterValue ? "generatedTryReadNextField" : "generatedTryReadField",
                        "([B)Z",
                        false);
            }
        }

        private byte[] fieldToken(RuntimeCodecPlan.MemberPlan member) {
            var extension = member.schema().getExtension(SmithyJsonSchemaExtensions.KEY);
            return useJsonName ? extension.jsonNameBytes() : extension.memberNameBytes();
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

        private void emitReaderBucket(
                RuntimeCodecPlan.StructPlan structure,
                int bucket,
                int bucketCount
        ) {
            List<RuntimeCodecPlan.MemberPlan> members = structure.members()
                    .stream()
                    .filter(member -> Math.floorMod(fieldHash(member.wireName(useJsonName)), bucketCount) == bucket)
                    .toList();
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    readerBucketName(structure, bucket),
                    readerBucketDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new LinkedHashMap<>();
            for (RuntimeCodecPlan.MemberPlan member : members) {
                groups.computeIfAbsent(fieldHash(member.wireName(useJsonName)), ignored -> new ArrayList<>())
                        .add(member);
            }
            List<Integer> keys = groups.keySet().stream().sorted(Comparator.naturalOrder()).toList();
            int[] switchKeys = keys.stream().mapToInt(Integer::intValue).toArray();
            Label unknown = new Label();
            Label[] labels = keys.stream().map(ignored -> new Label()).toArray(Label[]::new);
            method.visitVarInsn(ILOAD, 3);
            method.visitLookupSwitchInsn(unknown, switchKeys, labels);
            for (int i = 0; i < keys.size(); i++) {
                method.visitLabel(labels[i]);
                for (RuntimeCodecPlan.MemberPlan member : groups.get(keys.get(i))) {
                    Label next = new Label();
                    method.visitVarInsn(ALOAD, 1);
                    method.visitFieldInsn(GETSTATIC, className, "N" + memberIds.get(member), "[B");
                    method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedFieldEquals", "([B)Z", false);
                    method.visitJumpInsn(IFEQ, next);
                    emitReadMember(method, member);
                    method.visitInsn(ICONST_1);
                    method.visitInsn(IRETURN);
                    method.visitLabel(next);
                }
                method.visitJumpInsn(GOTO, unknown);
            }
            method.visitLabel(unknown);
            method.visitInsn(ICONST_0);
            method.visitInsn(IRETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitReadMember(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            Class<?> parameter = member.setter().getParameterTypes()[0];
            Label skip = new Label();
            if (!parameter.isPrimitive()) {
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
                method.visitJumpInsn(IFNE, skip);
            }
            method.visitVarInsn(ALOAD, 2);
            emitReadValue(method, member, parameter);
            invoke(method, member.setter());
            if (member.setter().getReturnType() != void.class) {
                method.visitInsn(POP);
            }
            method.visitLabel(skip);
        }

        private void emitReadValue(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                Class<?> parameter
        ) {
            ShapeType type = member.target().type();
            switch (type) {
                case BOOLEAN -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadBoolean",
                            "()Z",
                            false);
                    box(method, parameter, Boolean.class, "(Z)Ljava/lang/Boolean;");
                }
                case BYTE -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadByte",
                            "()B",
                            false);
                    box(method, parameter, Byte.class, "(B)Ljava/lang/Byte;");
                }
                case SHORT -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadShort",
                            "()S",
                            false);
                    box(method, parameter, Short.class, "(S)Ljava/lang/Short;");
                }
                case INTEGER -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadInteger",
                            "()I",
                            false);
                    box(method, parameter, Integer.class, "(I)Ljava/lang/Integer;");
                }
                case LONG -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadLong",
                            "()J",
                            false);
                    box(method, parameter, Long.class, "(J)Ljava/lang/Long;");
                }
                case FLOAT -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadFloat",
                            "()F",
                            false);
                    box(method, parameter, Float.class, "(F)Ljava/lang/Float;");
                }
                case DOUBLE -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadDouble",
                            "()D",
                            false);
                    box(method, parameter, Double.class, "(D)Ljava/lang/Double;");
                }
                case BIG_INTEGER -> readObject(method, "readBigInteger", "Ljava/math/BigInteger;");
                case BIG_DECIMAL -> readObject(method, "readBigDecimal", "Ljava/math/BigDecimal;");
                case STRING -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadString",
                            "()Ljava/lang/String;",
                            false);
                }
                case ENUM -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            enumReaderName(member.target().shapeClass()),
                            "(L" + READER + ";)L" + Type.getInternalName(member.target().shapeClass()) + ";",
                            false);
                }
                case BLOB -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadBlob",
                            "()L" + BYTE_BUFFER + ";",
                            false);
                }
                case TIMESTAMP -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            timestampReader(member.schema()),
                            "()L" + Type.getInternalName(Instant.class) + ";",
                            false);
                }
                case DOCUMENT -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readDocument",
                            "()L" + DOCUMENT + ";",
                            false);
                }
                case STRUCTURE -> emitReadStructure(method, member);
                case UNION -> {
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(member.target().id());
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            unionValueReaderName(nested),
                            "(L" + READER + ";)L" + Type.getInternalName(nested.shapeClass()) + ";",
                            false);
                }
                case LIST, SET -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateReaderName(member.target()),
                            "(L" + READER + ";)Ljava/util/List;",
                            false);
                }
                case MAP -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateReaderName(member.target()),
                            "(L" + READER + ";)Ljava/util/Map;",
                            false);
                }
                default -> throw new AssertionError(type);
            }
        }

        private void emitReadTarget(
                MethodVisitor method,
                Schema schema,
                int readerLocal
        ) {
            var target = schema.isMember() ? schema.memberTarget() : schema;
            switch (target.type()) {
                case BOOLEAN -> {
                    readGeneratedPrimitive(method, readerLocal, "generatedReadBoolean", "Z");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Boolean",
                            "valueOf",
                            "(Z)Ljava/lang/Boolean;",
                            false);
                }
                case BYTE -> {
                    readGeneratedPrimitive(method, readerLocal, "generatedReadByte", "B");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Byte",
                            "valueOf",
                            "(B)Ljava/lang/Byte;",
                            false);
                }
                case SHORT -> {
                    readGeneratedPrimitive(method, readerLocal, "generatedReadShort", "S");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Short",
                            "valueOf",
                            "(S)Ljava/lang/Short;",
                            false);
                }
                case INTEGER -> {
                    readGeneratedPrimitive(method, readerLocal, "generatedReadInteger", "I");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Integer",
                            "valueOf",
                            "(I)Ljava/lang/Integer;",
                            false);
                }
                case LONG -> {
                    readGeneratedPrimitive(method, readerLocal, "generatedReadLong", "J");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Long",
                            "valueOf",
                            "(J)Ljava/lang/Long;",
                            false);
                }
                case FLOAT -> {
                    readGeneratedPrimitive(method, readerLocal, "generatedReadFloat", "F");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Float",
                            "valueOf",
                            "(F)Ljava/lang/Float;",
                            false);
                }
                case DOUBLE -> {
                    readGeneratedPrimitive(method, readerLocal, "generatedReadDouble", "D");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Double",
                            "valueOf",
                            "(D)Ljava/lang/Double;",
                            false);
                }
                case BIG_INTEGER -> readObject(method, readerLocal, "readBigInteger", "Ljava/math/BigInteger;");
                case BIG_DECIMAL -> readObject(method, readerLocal, "readBigDecimal", "Ljava/math/BigDecimal;");
                case STRING -> {
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadString",
                            "()Ljava/lang/String;",
                            false);
                }
                case ENUM -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            enumReaderName(target.shapeClass()),
                            "(L" + READER + ";)L" + Type.getInternalName(target.shapeClass()) + ";",
                            false);
                }
                case BLOB -> {
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadBlob",
                            "()L" + BYTE_BUFFER + ";",
                            false);
                }
                case TIMESTAMP -> {
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            timestampReader(schema),
                            "()L" + Type.getInternalName(Instant.class) + ";",
                            false);
                }
                case DOCUMENT -> {
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readDocument",
                            "()L" + DOCUMENT + ";",
                            false);
                }
                case LIST, SET -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateReaderName(target),
                            "(L" + READER + ";)Ljava/util/List;",
                            false);
                }
                case MAP -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            aggregateReaderName(target),
                            "(L" + READER + ";)Ljava/util/Map;",
                            false);
                }
                case STRUCTURE -> emitReadStructureValue(method, target, readerLocal);
                case UNION -> {
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(target.id());
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            unionValueReaderName(nested),
                            "(L" + READER + ";)L" + Type.getInternalName(nested.shapeClass()) + ";",
                            false);
                }
                default -> throw new AssertionError(target.type());
            }
        }

        private void readGeneratedPrimitive(
                MethodVisitor method,
                int readerLocal,
                String name,
                String returnDescriptor
        ) {
            method.visitVarInsn(ALOAD, readerLocal);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    name,
                    "()" + returnDescriptor,
                    false);
        }

        private void readObject(
                MethodVisitor method,
                int readerLocal,
                String name,
                String returnDescriptor
        ) {
            method.visitVarInsn(ALOAD, readerLocal);
            method.visitInsn(ACONST_NULL);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    name,
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;)" + returnDescriptor,
                    false);
        }

        private void readObject(MethodVisitor method, String name, String returnDescriptor) {
            method.visitVarInsn(ALOAD, 1);
            method.visitInsn(ACONST_NULL);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    name,
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;)" + returnDescriptor,
                    false);
        }

        private void emitReadStructure(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            emitReadStructureValue(method, member.target(), 1);
        }

        private void emitReadStructureValue(
                MethodVisitor method,
                Schema schema,
                int readerLocal
        ) {
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(target.id());
            Method factory = nested.builderFactory();
            invoke(method, factory);
            method.visitVarInsn(ASTORE, STRUCTURE_BUILDER_LOCAL);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, readerLocal);
            method.visitVarInsn(ALOAD, STRUCTURE_BUILDER_LOCAL);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    readerName(nested),
                    readerDescriptor(nested),
                    false);
            method.visitVarInsn(ALOAD, STRUCTURE_BUILDER_LOCAL);
            Method build = findBuild(nested.builderClass(), nested.shapeClass());
            invoke(method, build);
        }

        private static Method findBuild(Class<?> builderClass, Class<?> shapeClass) {
            for (Method method : builderClass.getMethods()) {
                if (method.getName().equals("build")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == shapeClass) {
                    return method;
                }
            }
            throw new UnsupportedSchemaException("No direct build method on " + builderClass.getName());
        }

        private static void box(
                MethodVisitor method,
                Class<?> parameter,
                Class<?> boxed,
                String descriptor
        ) {
            if (!parameter.isPrimitive()) {
                method.visitMethodInsn(
                        INVOKESTATIC,
                        Type.getInternalName(boxed),
                        "valueOf",
                        descriptor,
                        false);
            }
        }

        private static void invoke(MethodVisitor method, Method target) {
            Class<?> owner = target.getDeclaringClass();
            boolean itf = owner.isInterface();
            boolean isStatic = Modifier.isStatic(target.getModifiers());
            method.visitMethodInsn(
                    isStatic ? INVOKESTATIC : (itf ? INVOKEINTERFACE : INVOKEVIRTUAL),
                    Type.getInternalName(owner),
                    target.getName(),
                    Type.getMethodDescriptor(target),
                    itf);
        }

        private String writerName(RuntimeCodecPlan.StructPlan structure) {
            return "writeS" + structureIds.get(structure);
        }

        private String writerChunkName(RuntimeCodecPlan.StructPlan structure, int chunk) {
            return writerName(structure) + "C" + chunk;
        }

        private static String writerDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + Type.getInternalName(structure.shapeClass()) + ";L" + WRITER + ";)V";
        }

        private static String writerChunkDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + Type.getInternalName(structure.shapeClass()) + ";L" + WRITER + ";I)I";
        }

        private String readerName(RuntimeCodecPlan.StructPlan structure) {
            return "readS" + structureIds.get(structure);
        }

        private String readerBucketName(RuntimeCodecPlan.StructPlan structure, int bucket) {
            return readerName(structure) + "B" + bucket;
        }

        private String unionValueReaderName(RuntimeCodecPlan.StructPlan structure) {
            return "readU" + structureIds.get(structure);
        }

        private String aggregateWriterName(Schema schema) {
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            return "writeA" + aggregateIds.get(target.id());
        }

        private String aggregateReaderName(Schema schema) {
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            return "readA" + aggregateIds.get(target.id());
        }

        private String enumReaderName(Class<?> enumClass) {
            return "readE" + enumIds.get(enumClass);
        }

        private static String readerDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + READER + ";L" + Type.getInternalName(structure.builderClass()) + ";)V";
        }

        private static String readerBucketDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + READER + ";L" + Type.getInternalName(structure.builderClass()) + ";I)Z";
        }

        private static int readerBucketCount(RuntimeCodecPlan.StructPlan structure) {
            return Math.max(
                    structure.readerBuckets(),
                    Math.max(1, (structure.members().size() + 7) / 8));
        }

        private static int fieldHash(String value) {
            int hash = 0;
            for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
                hash = 31 * hash + (b & 0xff);
            }
            return hash;
        }

        private int timestampFormat(RuntimeCodecPlan.MemberPlan member) {
            return timestampFormat(member.schema());
        }

        private String timestampReader(Schema schema) {
            return switch (timestampFormat(schema)) {
                case 1 -> "generatedReadDateTimeTimestamp";
                case 2 -> "generatedReadHttpDateTimestamp";
                default -> "generatedReadEpochTimestamp";
            };
        }

        private int timestampFormat(Schema schema) {
            TimestampFormatter formatter = settings.timestampResolver().resolve(schema);
            return switch (formatter.format()) {
                case DATE_TIME -> 1;
                case HTTP_DATE -> 2;
                default -> 0;
            };
        }

        private static void emitThrow(MethodVisitor method, String message) {
            method.visitTypeInsn(NEW, "software/amazon/smithy/java/core/serde/SerializationException");
            method.visitInsn(DUP);
            method.visitLdcInsn(message);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    "software/amazon/smithy/java/core/serde/SerializationException",
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false);
            method.visitInsn(ATHROW);
        }

    }
}
