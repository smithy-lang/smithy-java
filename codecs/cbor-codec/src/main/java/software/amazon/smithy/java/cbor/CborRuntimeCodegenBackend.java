/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ACC_FINAL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ACC_PRIVATE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ACC_PUBLIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ACC_STATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ACC_SUPER;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ACONST_NULL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ALOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ARETURN;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ASTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.CHECKCAST;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.DLOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.DSTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.FLOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.FSTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.GETSTATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.GOTO;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IFEQ;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IFNE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IFNONNULL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ILOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKEINTERFACE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKESPECIAL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKESTATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKEVIRTUAL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ISTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.LLOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.LSTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.POP;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.PUTSTATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.RETURN;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.V17;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
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
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Type;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.model.shapes.ShapeType;

final class CborRuntimeCodegenBackend implements RuntimeCodecBackend<GeneratedCborCodec> {
    private static final String CODEC = Type.getInternalName(GeneratedCborCodec.class);
    private static final String WRITER = Type.getInternalName(CborSerializer.class);
    private static final String READER = Type.getInternalName(CborDeserializer.class);
    private static final String SETTINGS = Type.getInternalName(CborSettings.class);
    private static final String SERIALIZABLE_SHAPE = Type.getInternalName(SerializableShape.class);
    private static final String SHAPE_BUILDER = Type.getInternalName(ShapeBuilder.class);
    private static final int MEMBERS_PER_METHOD = 8;

    @Override
    public String id() {
        return "cbor";
    }

    @Override
    public Class<GeneratedCborCodec> codecType() {
        return GeneratedCborCodec.class;
    }

    @Override
    public Class<?> lookupHost() {
        return GeneratedCborCodec.class;
    }

    @Override
    public Emission emit(RuntimeCodecPlan plan, String generatedName) {
        validate(plan);
        return new Generator(plan, generatedName).generate();
    }

    private static void validate(RuntimeCodecPlan plan) {
        if (plan.structures().size() != 1 || plan.rootStructure().union()) {
            throw new UnsupportedSchemaException("CBOR runtime codegen slice supports scalar structures only");
        }
        for (RuntimeCodecPlan.MemberPlan member : plan.rootStructure().members()) {
            switch (member.target().type()) {
                case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL,
                        STRING, BLOB, TIMESTAMP ->
                    {
                    }
                default -> throw new UnsupportedSchemaException(
                        "CBOR runtime codegen slice does not lower " + member.target().type());
            }
        }
    }

    private static final class Generator {
        private final RuntimeCodecPlan plan;
        private final RuntimeCodecPlan.StructPlan root;
        private final String className;
        private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        private final IdentityHashMap<RuntimeCodecPlan.MemberPlan, Integer> memberIds = new IdentityHashMap<>();
        private int methodCount;

        private Generator(RuntimeCodecPlan plan, String className) {
            this.plan = plan;
            this.root = plan.rootStructure();
            this.className = className;
            for (RuntimeCodecPlan.MemberPlan member : root.members()) {
                memberIds.put(member, memberIds.size());
            }
        }

        private Emission generate() {
            writer.visit(V17, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Object", new String[] {CODEC});
            emitFields();
            emitConstructor();
            emitClassInitializer();
            emitWriter();
            emitReader();
            emitWriteEntry();
            emitReadEntry();
            writer.visitEnd();
            return new Emission(writer.toByteArray(), methodCount);
        }

        private void emitFields() {
            for (RuntimeCodecPlan.MemberPlan member : root.members()) {
                int id = memberIds.get(member);
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "N" + id, "[B", null, null).visitEnd();
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "E" + id, "[B", null, null).visitEnd();
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
            for (RuntimeCodecPlan.MemberPlan member : root.members()) {
                int id = memberIds.get(member);
                String name = member.memberName();
                method.visitLdcInsn(name);
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
                method.visitFieldInsn(PUTSTATIC, className, "N" + id, "[B");
                method.visitLdcInsn(name);
                method.visitMethodInsn(
                        INVOKESTATIC,
                        WRITER,
                        "encodeMemberName",
                        "(Ljava/lang/String;)[B",
                        false);
                method.visitFieldInsn(PUTSTATIC, className, "E" + id, "[B");
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWriter() {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "writeS0",
                    "(L" + Type.getInternalName(root.shapeClass()) + ";L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedBeginObject", "()V", false);
            int chunks = Math.max(1, (root.members().size() + MEMBERS_PER_METHOD - 1) / MEMBERS_PER_METHOD);
            for (int chunk = 0; chunk < chunks; chunk++) {
                method.visitVarInsn(ALOAD, 0);
                method.visitVarInsn(ALOAD, 1);
                method.visitVarInsn(ALOAD, 2);
                method.visitMethodInsn(
                        INVOKESPECIAL,
                        className,
                        "writeC" + chunk,
                        "(L" + Type.getInternalName(root.shapeClass()) + ";L" + WRITER + ";)V",
                        false);
            }
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedEndObject", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;

            for (int chunk = 0; chunk < chunks; chunk++) {
                emitWriterChunk(chunk);
            }
        }

        private void emitWriterChunk(int chunk) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "writeC" + chunk,
                    "(L" + Type.getInternalName(root.shapeClass()) + ";L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            int start = chunk * MEMBERS_PER_METHOD;
            int end = Math.min(root.members().size(), start + MEMBERS_PER_METHOD);
            for (int i = start; i < end; i++) {
                emitWriteMember(method, root.members().get(i));
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWriteMember(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            Label done = new Label();
            if (member.presence() != null) {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.presence());
                method.visitJumpInsn(IFEQ, done);
            }
            Class<?> valueType = member.getter().getReturnType();
            method.visitVarInsn(ALOAD, 1);
            invoke(method, member.getter());
            int local = 3;
            method.visitVarInsn(storeOpcode(valueType), local);
            if (!valueType.isPrimitive()) {
                Label write = new Label();
                method.visitVarInsn(ALOAD, local);
                method.visitJumpInsn(IFNONNULL, write);
                method.visitJumpInsn(GOTO, done);
                method.visitLabel(write);
            }
            emitWriteValue(method, member, valueType, local);
            method.visitLabel(done);
        }

        private void emitWriteValue(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                Class<?> valueType,
                int local
        ) {
            method.visitVarInsn(ALOAD, 2);
            method.visitFieldInsn(GETSTATIC, className, "E" + memberIds.get(member), "[B");
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedWriteField", "([B)V", false);
            method.visitVarInsn(ALOAD, 2);
            method.visitInsn(ACONST_NULL);
            method.visitVarInsn(loadOpcode(valueType), local);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    WRITER,
                    writerMethod(member.target().type()),
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;" + Type.getDescriptor(valueType) + ")V",
                    false);
        }

        private void emitReader() {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    "readS0",
                    "(L" + READER + ";L" + Type.getInternalName(root.builderClass()) + ";)V",
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
            emitReadDispatch(method);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedObjectHasNext", "()Z", false);
            method.visitJumpInsn(IFNE, loop);
            method.visitLabel(done);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitReadDispatch(MethodVisitor method) {
            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new LinkedHashMap<>();
            for (RuntimeCodecPlan.MemberPlan member : root.members()) {
                groups.computeIfAbsent(fieldHash(member.memberName()), ignored -> new ArrayList<>()).add(member);
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
                for (RuntimeCodecPlan.MemberPlan member : groups.get(keys.get(i))) {
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

        private void emitReadMember(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            Class<?> parameter = member.setter().getParameterTypes()[0];
            Label done = new Label();
            if (!parameter.isPrimitive()) {
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
                method.visitJumpInsn(IFNE, done);
            }
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 1);
            method.visitInsn(ACONST_NULL);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    readerMethod(member.target().type()),
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;)" + Type.getDescriptor(parameter),
                    false);
            invoke(method, member.setter());
            if (member.setter().getReturnType() != void.class) {
                method.visitInsn(POP);
            }
            method.visitLabel(done);
        }

        private void emitWriteEntry() {
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "write",
                    "(L" + SERIALIZABLE_SHAPE + ";L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitTypeInsn(CHECKCAST, Type.getInternalName(root.shapeClass()));
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    "writeS0",
                    "(L" + Type.getInternalName(root.shapeClass()) + ";L" + WRITER + ";)V",
                    false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitReadEntry() {
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "read",
                    "([BL" + SHAPE_BUILDER + ";L" + SETTINGS + ";)L" + SERIALIZABLE_SHAPE + ";",
                    null,
                    null);
            method.visitCode();
            method.visitTypeInsn(
                    software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.NEW,
                    READER);
            method.visitInsn(software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.DUP);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(INVOKESPECIAL, READER, "<init>", "([BL" + SETTINGS + ";)V", false);
            method.visitVarInsn(ASTORE, 4);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 4);
            method.visitVarInsn(ALOAD, 2);
            method.visitTypeInsn(CHECKCAST, Type.getInternalName(root.builderClass()));
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    "readS0",
                    "(L" + READER + ";L" + Type.getInternalName(root.builderClass()) + ";)V",
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
                    "()L" + SERIALIZABLE_SHAPE + ";",
                    true);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private static String writerMethod(ShapeType type) {
            return switch (type) {
                case BOOLEAN -> "writeBoolean";
                case BYTE -> "writeByte";
                case SHORT -> "writeShort";
                case INTEGER -> "writeInteger";
                case LONG -> "writeLong";
                case FLOAT -> "writeFloat";
                case DOUBLE -> "writeDouble";
                case BIG_INTEGER -> "writeBigInteger";
                case BIG_DECIMAL -> "writeBigDecimal";
                case STRING -> "writeString";
                case BLOB -> "writeBlob";
                case TIMESTAMP -> "writeTimestamp";
                default -> throw new AssertionError(type);
            };
        }

        private static String readerMethod(ShapeType type) {
            return switch (type) {
                case BOOLEAN -> "readBoolean";
                case BYTE -> "readByte";
                case SHORT -> "readShort";
                case INTEGER -> "readInteger";
                case LONG -> "readLong";
                case FLOAT -> "readFloat";
                case DOUBLE -> "readDouble";
                case BIG_INTEGER -> "readBigInteger";
                case BIG_DECIMAL -> "readBigDecimal";
                case STRING -> "readString";
                case BLOB -> "readBlob";
                case TIMESTAMP -> "readTimestamp";
                default -> throw new AssertionError(type);
            };
        }

        private static int fieldHash(String value) {
            int hash = 0;
            for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
                hash = 31 * hash + (b & 0xff);
            }
            return hash;
        }

        private static int storeOpcode(Class<?> type) {
            if (type == long.class) {
                return LSTORE;
            } else if (type == float.class) {
                return FSTORE;
            } else if (type == double.class) {
                return DSTORE;
            } else if (type.isPrimitive()) {
                return ISTORE;
            }
            return ASTORE;
        }

        private static int loadOpcode(Class<?> type) {
            if (type == long.class) {
                return LLOAD;
            } else if (type == float.class) {
                return FLOAD;
            } else if (type == double.class) {
                return DLOAD;
            } else if (type.isPrimitive()) {
                return ILOAD;
            }
            return ALOAD;
        }

        private static void invoke(MethodVisitor method, Method target) {
            Class<?> owner = target.getDeclaringClass();
            boolean itf = owner.isInterface();
            int opcode = Modifier.isStatic(target.getModifiers())
                    ? INVOKESTATIC
                    : (itf ? INVOKEINTERFACE : INVOKEVIRTUAL);
            method.visitMethodInsn(
                    opcode,
                    Type.getInternalName(owner),
                    target.getName(),
                    Type.getMethodDescriptor(target),
                    itf);
        }
    }
}
