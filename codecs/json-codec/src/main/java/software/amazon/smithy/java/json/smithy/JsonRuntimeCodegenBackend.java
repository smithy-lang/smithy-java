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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecPlan;
import software.amazon.smithy.java.codecs.commons.internal.codegen.UnsupportedSchemaException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;

final class JsonRuntimeCodegenBackend implements RuntimeCodecBackend<GeneratedJsonCodec>, Opcodes {
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
        private final IdentityHashMap<Object, RuntimeCodecPlan.StructPlan> structuresBySchema = new IdentityHashMap<>();
        private final IdentityHashMap<Object, Integer> aggregateIds = new IdentityHashMap<>();
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
                structuresBySchema.put(structure.schema(), structure);
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
                    if (aggregateIds.put(schema, aggregateIds.size()) == null) {
                        orderedAggregates.add(schema);
                        collectTarget(schema.listMember());
                    }
                }
                case MAP -> {
                    if (aggregateIds.put(schema, aggregateIds.size()) == null) {
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
                String name = member.wireName(useJsonName);
                emitUtf8(method, name);
                method.visitFieldInsn(PUTSTATIC, className, "N" + id, "[B");
                emitUtf8(method, "\"" + escape(name) + "\":");
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
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "element", "()V", false);
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
            method.visitTypeInsn(NEW, "java/util/ArrayList");
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
            method.visitVarInsn(ASTORE, 2);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginArray", "()Z", false);
            Label done = new Label();
            Label loop = new Label();
            method.visitJumpInsn(IFEQ, done);
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
            Label value = new Label();
            Label add = new Label();
            method.visitJumpInsn(IFEQ, value);
            method.visitInsn(ACONST_NULL);
            method.visitJumpInsn(GOTO, add);
            method.visitLabel(value);
            emitReadTarget(method, schema.listMember(), 1);
            method.visitLabel(add);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/List",
                    "add",
                    "(Ljava/lang/Object;)Z",
                    true);
            method.visitInsn(POP);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedArrayHasNext", "()Z", false);
            method.visitJumpInsn(IFNE, loop);
            method.visitLabel(done);
            method.visitVarInsn(ALOAD, 2);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
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
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    WRITER,
                    "dynamicField",
                    "(Ljava/lang/String;)V",
                    false);
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
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedReadFieldHash", "()I", false);
            method.visitInsn(POP);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    "generatedFieldName",
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
            List<RuntimeCodecPlan.MemberPlan> members = structure.members();
            int chunks = Math.max(1, (members.size() + 7) / 8);
            for (int chunk = 0; chunk < chunks; chunk++) {
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
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "endObject", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;

            for (int chunk = 0; chunk < chunks; chunk++) {
                MethodVisitor chunkMethod = writer.visitMethod(
                        ACC_PRIVATE,
                        writerChunkName(structure, chunk),
                        writerDescriptor(structure),
                        null,
                        null);
                chunkMethod.visitCode();
                int end = Math.min(members.size(), (chunk + 1) * 8);
                for (int i = chunk * 8; i < end; i++) {
                    emitWriteMember(chunkMethod, members.get(i));
                }
                chunkMethod.visitInsn(RETURN);
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
                emitField(method, member);
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

        private void emitWriteMember(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
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
                method.visitVarInsn(ASTORE, 3);
                method.visitVarInsn(ALOAD, 3);
                method.visitJumpInsn(IFNULL, skip);
                emitField(method, member);
                if (member.target().type() == ShapeType.STRUCTURE
                        || member.target().type() == ShapeType.UNION) {
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(member.target());
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, 3);
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
                    method.visitVarInsn(ALOAD, 3);
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
                    method.visitVarInsn(ALOAD, 3);
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
                    method.visitVarInsn(ALOAD, 3);
                    emitWriteValue(method, member, returnType);
                }
                method.visitLabel(skip);
                return;
            }
            emitField(method, member);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 1);
            invoke(method, member.getter());
            emitWriteValue(method, member, returnType);
            method.visitLabel(skip);
        }

        private void emitField(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            method.visitVarInsn(ALOAD, 2);
            method.visitFieldInsn(GETSTATIC, className, "W" + memberIds.get(member), "[B");
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "field", "([B)V", false);
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
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(target);
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
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedReadFieldHash", "()I", false);
            method.visitVarInsn(ISTORE, 3);
            int buckets = readerBucketCount(structure);
            if (buckets == 1) {
                emitReadDispatch(method, structure);
            } else {
                Label dispatched = new Label();
                for (int bucket = 0; bucket < buckets; bucket++) {
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
                    method.visitJumpInsn(IFNE, dispatched);
                }
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

        private void emitReaderBucket(
                RuntimeCodecPlan.StructPlan structure,
                int bucket,
                int bucketCount
        ) {
            int chunkSize = (structure.members().size() + bucketCount - 1) / bucketCount;
            int start = bucket * chunkSize;
            int end = Math.min(structure.members().size(), start + chunkSize);
            List<RuntimeCodecPlan.MemberPlan> members = structure.members().subList(start, end);
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
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readBoolean",
                            "(Lsoftware/amazon/smithy/java/core/schema/Schema;)Z",
                            false);
                    box(method, parameter, Boolean.class, "(Z)Ljava/lang/Boolean;");
                }
                case BYTE -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readByte",
                            "(Lsoftware/amazon/smithy/java/core/schema/Schema;)B",
                            false);
                    box(method, parameter, Byte.class, "(B)Ljava/lang/Byte;");
                }
                case SHORT -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readShort",
                            "(Lsoftware/amazon/smithy/java/core/schema/Schema;)S",
                            false);
                    box(method, parameter, Short.class, "(S)Ljava/lang/Short;");
                }
                case INTEGER -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readInteger",
                            "(Lsoftware/amazon/smithy/java/core/schema/Schema;)I",
                            false);
                    box(method, parameter, Integer.class, "(I)Ljava/lang/Integer;");
                }
                case LONG -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readLong",
                            "(Lsoftware/amazon/smithy/java/core/schema/Schema;)J",
                            false);
                    box(method, parameter, Long.class, "(J)Ljava/lang/Long;");
                }
                case FLOAT -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readFloat",
                            "(Lsoftware/amazon/smithy/java/core/schema/Schema;)F",
                            false);
                    box(method, parameter, Float.class, "(F)Ljava/lang/Float;");
                }
                case DOUBLE -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "readDouble",
                            "(Lsoftware/amazon/smithy/java/core/schema/Schema;)D",
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
                case BLOB -> readObject(method, "readBlob", "L" + BYTE_BUFFER + ";");
                case TIMESTAMP -> {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitLdcInsn(timestampFormat(member));
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadTimestamp",
                            "(I)L" + Type.getInternalName(Instant.class) + ";",
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
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(member.target());
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
                    readPrimitive(method, readerLocal, "readBoolean", "Z");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Boolean",
                            "valueOf",
                            "(Z)Ljava/lang/Boolean;",
                            false);
                }
                case BYTE -> {
                    readPrimitive(method, readerLocal, "readByte", "B");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Byte",
                            "valueOf",
                            "(B)Ljava/lang/Byte;",
                            false);
                }
                case SHORT -> {
                    readPrimitive(method, readerLocal, "readShort", "S");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Short",
                            "valueOf",
                            "(S)Ljava/lang/Short;",
                            false);
                }
                case INTEGER -> {
                    readPrimitive(method, readerLocal, "readInteger", "I");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Integer",
                            "valueOf",
                            "(I)Ljava/lang/Integer;",
                            false);
                }
                case LONG -> {
                    readPrimitive(method, readerLocal, "readLong", "J");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Long",
                            "valueOf",
                            "(J)Ljava/lang/Long;",
                            false);
                }
                case FLOAT -> {
                    readPrimitive(method, readerLocal, "readFloat", "F");
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Float",
                            "valueOf",
                            "(F)Ljava/lang/Float;",
                            false);
                }
                case DOUBLE -> {
                    readPrimitive(method, readerLocal, "readDouble", "D");
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
                case BLOB -> readObject(method, readerLocal, "readBlob", "L" + BYTE_BUFFER + ";");
                case TIMESTAMP -> {
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitLdcInsn(timestampFormat(schema));
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedReadTimestamp",
                            "(I)L" + Type.getInternalName(Instant.class) + ";",
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
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(target);
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

        private void readPrimitive(MethodVisitor method, int readerLocal, String name, String returnDescriptor) {
            method.visitVarInsn(ALOAD, readerLocal);
            method.visitInsn(ACONST_NULL);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    name,
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;)" + returnDescriptor,
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
            RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(schema);
            Method factory = nested.builderFactory();
            method.visitMethodInsn(
                    INVOKESTATIC,
                    Type.getInternalName(nested.shapeClass()),
                    factory.getName(),
                    Type.getMethodDescriptor(factory),
                    false);
            method.visitVarInsn(ASTORE, 6);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, readerLocal);
            method.visitVarInsn(ALOAD, 6);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    readerName(nested),
                    readerDescriptor(nested),
                    false);
            method.visitVarInsn(ALOAD, 6);
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
            return "writeA" + aggregateIds.get(schema.isMember() ? schema.memberTarget() : schema);
        }

        private String aggregateReaderName(Schema schema) {
            return "readA" + aggregateIds.get(schema.isMember() ? schema.memberTarget() : schema);
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

        private static String escape(String value) {
            StringBuilder result = new StringBuilder(value.length() + 8);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> result.append("\\\"");
                    case '\\' -> result.append("\\\\");
                    case '\b' -> result.append("\\b");
                    case '\f' -> result.append("\\f");
                    case '\n' -> result.append("\\n");
                    case '\r' -> result.append("\\r");
                    case '\t' -> result.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            result.append(String.format("\\u%04x", (int) c));
                        } else {
                            result.append(c);
                        }
                    }
                }
            }
            return result.toString();
        }
    }
}
