/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.emitDefaultConstructor;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.emitThrow;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.fieldHash;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.findBuild;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.invoke;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.loadOpcode;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.storeOpcode;
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
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.DUP;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.GETSTATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.GOTO;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ICONST_0;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ICONST_1;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IFEQ;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IFNE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IFNONNULL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IF_ICMPEQ;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IF_ICMPGE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ILOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INSTANCEOF;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKEINTERFACE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKESPECIAL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKESTATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKEVIRTUAL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IRETURN;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ISTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.NEW;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.POP;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.PUTSTATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.RETURN;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.V17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecPlan;
import software.amazon.smithy.java.codecs.commons.internal.codegen.UnsupportedSchemaException;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassWriter;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Label;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.MethodVisitor;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Type;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

final class CborRuntimeCodegenBackend implements RuntimeCodecBackend<GeneratedCborCodec> {
    private static final String CODEC = Type.getInternalName(GeneratedCborCodec.class);
    private static final String WRITER = Type.getInternalName(CborSerializer.class);
    private static final String READER = Type.getInternalName(CborDeserializer.class);
    private static final String MAP_CONSUMER = Type.getInternalName(MapWriteConsumer.class);
    private static final String SETTINGS = Type.getInternalName(CborSettings.class);
    private static final String SERIALIZABLE_SHAPE = Type.getInternalName(SerializableShape.class);
    private static final String SHAPE_BUILDER = Type.getInternalName(ShapeBuilder.class);
    private static final String DOCUMENT = Type.getInternalName(Document.class);
    private static final String SMITHY_ENUM = Type.getInternalName(SmithyEnum.class);
    private static final String SMITHY_INT_ENUM = Type.getInternalName(SmithyIntEnum.class);
    private static final String BYTE_BUFFER = Type.getInternalName(ByteBuffer.class);
    private static final String MISSING_UNION_MEMBER = "Union object must contain one member";

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
        return CborDeserializer.class;
    }

    @Override
    public Budgets budgets() {
        return new Budgets(220, 300, 8, 8);
    }

    @Override
    public Emission emit(RuntimeCodecPlan plan, String generatedName) {
        validate(plan);
        return new Generator(plan, generatedName).generate();
    }

    private static void validate(RuntimeCodecPlan plan) {
        for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
            if (structure.builderFactory() == null && structure.schema() != plan.root() && !structure.union()) {
                throw new UnsupportedSchemaException("No public builder factory for " + structure.schema().id());
            }
            rejectModeledException(structure);
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                switch (member.target().type()) {
                    case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL,
                            STRING, ENUM, INT_ENUM, BLOB, TIMESTAMP, DOCUMENT, LIST, SET, MAP, STRUCTURE,
                            UNION ->
                        {
                        }
                    default -> throw new UnsupportedSchemaException(
                            "CBOR runtime codegen does not yet lower " + member.target().type()
                                    + " at " + member.schema().id());
                }
            }
        }
    }

    private static void rejectModeledException(RuntimeCodecPlan.StructPlan structure) {
        Class<?> shapeClass = structure.shapeClass();
        // Generated readers bypass ModeledException.deserialized().
        if (shapeClass != null && ModeledException.class.isAssignableFrom(shapeClass)) {
            throw new UnsupportedSchemaException(
                    "CBOR runtime codegen does not support modeled exceptions: " + structure.schema().id());
        }
    }

    private static final class Generator {
        private final RuntimeCodecPlan plan;
        private final String className;
        private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        private final IdentityHashMap<RuntimeCodecPlan.StructPlan, Integer> structureIds = new IdentityHashMap<>();
        private final IdentityHashMap<RuntimeCodecPlan.MemberPlan, Integer> memberIds = new IdentityHashMap<>();
        private final List<RuntimeCodecPlan.MemberPlan> orderedMembers = new ArrayList<>();
        private final Map<ShapeId, RuntimeCodecPlan.StructPlan> structuresBySchema = new LinkedHashMap<>();
        private final Map<ShapeId, Integer> aggregateIds = new LinkedHashMap<>();
        private final List<Schema> orderedAggregates = new ArrayList<>();
        private final Map<Class<?>, Integer> enumIds = new LinkedHashMap<>();
        private final Map<Class<?>, Integer> intEnumIds = new LinkedHashMap<>();
        private int methodCount;

        private Generator(RuntimeCodecPlan plan, String className) {
            this.plan = plan;
            this.className = className;
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
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            switch (target.type()) {
                case LIST, SET -> {
                    if (!aggregateIds.containsKey(target.id())) {
                        aggregateIds.put(target.id(), aggregateIds.size());
                        orderedAggregates.add(target);
                        collectTarget(target.listMember());
                    }
                }
                case MAP -> {
                    if (!aggregateIds.containsKey(target.id())) {
                        aggregateIds.put(target.id(), aggregateIds.size());
                        orderedAggregates.add(target);
                        collectTarget(target.mapValueMember());
                    }
                }
                case ENUM -> enumIds.computeIfAbsent(target.shapeClass(), ignored -> enumIds.size());
                case INT_ENUM -> intEnumIds.computeIfAbsent(target.shapeClass(), ignored -> intEnumIds.size());
                default -> {
                }
            }
        }

        private Emission generate() {
            writer.visit(V17, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Object", new String[] {CODEC});
            emitFields();
            emitConstructor();
            emitClassInitializer();
            emitEnumReaders();
            emitIntEnumReaders();
            emitAggregateMethods();
            RuntimeCodecPlan.StructPlan root = plan.rootStructure();
            for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
                emitWriter(structure);
                boolean hasReaderBody = false;
                if (structure == root) {
                    emitReader(structure);
                    hasReaderBody = true;
                }
                if (structure.union()) {
                    emitUnionValueReader(structure);
                } else if (structure.builderFactory() != null && needsStructureValueReader(structure, root)) {
                    emitStructureValueReader(structure);
                    hasReaderBody = true;
                }
                if (hasReaderBody) {
                    emitReaderBuckets(structure);
                }
            }
            emitWriteEntry();
            emitReadEntry();
            writer.visitEnd();
            return new Emission(writer.toByteArray(), methodCount);
        }

        private boolean needsStructureValueReader(
                RuntimeCodecPlan.StructPlan structure,
                RuntimeCodecPlan.StructPlan root
        ) {
            if (structure != root) {
                return true;
            }
            for (RuntimeCodecPlan.StructPlan candidate : plan.structures()) {
                for (RuntimeCodecPlan.MemberPlan member : candidate.members()) {
                    if (containsStructure(member.target(), structure.schema().id())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean containsStructure(Schema schema, ShapeId target) {
            Schema value = schema.isMember() ? schema.memberTarget() : schema;
            if (value.id().equals(target)) {
                return true;
            }
            return switch (value.type()) {
                case LIST, SET -> containsStructure(value.listMember(), target);
                case MAP -> containsStructure(value.mapValueMember(), target);
                default -> false;
            };
        }

        private void emitFields() {
            for (RuntimeCodecPlan.MemberPlan member : orderedMembers) {
                int id = memberIds.get(member);
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "N" + id, "[B", null, null).visitEnd();
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "E" + id, "[B", null, null).visitEnd();
            }
        }

        private void emitConstructor() {
            emitDefaultConstructor(writer);
            methodCount++;
        }

        private void emitClassInitializer() {
            MethodVisitor method = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            method.visitCode();
            for (RuntimeCodecPlan.MemberPlan member : orderedMembers) {
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

        private void emitEnumReaders() {
            for (Class<?> enumClass : enumIds.keySet()) {
                Method unknown;
                try {
                    unknown = enumClass.getMethod("unknown", String.class);
                } catch (NoSuchMethodException e) {
                    throw new UnsupportedSchemaException("Generated enum lacks unknown method: "
                            + enumClass.getName());
                }
                List<EnumConstant> constants = enumConstants(enumClass);
                MethodVisitor method = writer.visitMethod(
                        ACC_PRIVATE,
                        enumReaderName(enumClass),
                        "(L" + READER + ";)L" + Type.getInternalName(enumClass) + ";",
                        null,
                        null);
                method.visitCode();
                method.visitVarInsn(ALOAD, 1);
                method.visitInsn(ACONST_NULL);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        "readString",
                        "(Lsoftware/amazon/smithy/java/core/schema/Schema;)Ljava/lang/String;",
                        false);
                method.visitVarInsn(ASTORE, 2);
                for (EnumConstant constant : constants) {
                    Label next = new Label();
                    method.visitLdcInsn(constant.value());
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            "java/lang/String",
                            "equals",
                            "(Ljava/lang/Object;)Z",
                            false);
                    method.visitJumpInsn(IFEQ, next);
                    method.visitFieldInsn(
                            GETSTATIC,
                            Type.getInternalName(enumClass),
                            constant.field().getName(),
                            "L" + Type.getInternalName(enumClass) + ";");
                    method.visitInsn(ARETURN);
                    method.visitLabel(next);
                }
                method.visitVarInsn(ALOAD, 2);
                invoke(method, unknown);
                method.visitInsn(ARETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                methodCount++;
            }
        }

        private static List<EnumConstant> enumConstants(Class<?> enumClass) {
            List<EnumConstant> result = new ArrayList<>();
            for (Field field : enumClass.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == enumClass) {
                    try {
                        result.add(new EnumConstant(field, ((SmithyEnum) field.get(null)).getValue()));
                    } catch (IllegalAccessException e) {
                        throw new UnsupportedSchemaException("Cannot access enum constant "
                                + enumClass.getName() + "." + field.getName());
                    }
                }
            }
            result.sort(Comparator.comparing(constant -> constant.field().getName()));
            return result;
        }

        private record EnumConstant(Field field, String value) {}

        private void emitIntEnumReaders() {
            for (Map.Entry<Class<?>, Integer> entry : intEnumIds.entrySet()) {
                Class<?> enumClass = entry.getKey();
                Method unknown;
                try {
                    unknown = enumClass.getMethod("unknown", int.class);
                } catch (NoSuchMethodException e) {
                    throw new UnsupportedSchemaException("Generated intEnum lacks unknown method: "
                            + enumClass.getName());
                }
                List<IntEnumConstant> constants = intEnumConstants(enumClass);
                MethodVisitor method = writer.visitMethod(
                        ACC_PRIVATE,
                        intEnumReaderName(enumClass),
                        "(L" + READER + ";)L" + Type.getInternalName(enumClass) + ";",
                        null,
                        null);
                method.visitCode();
                method.visitVarInsn(ALOAD, 1);
                method.visitInsn(ACONST_NULL);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        "readInteger",
                        "(Lsoftware/amazon/smithy/java/core/schema/Schema;)I",
                        false);
                method.visitVarInsn(ISTORE, 2);
                Label unknownValue = new Label();
                if (!constants.isEmpty()) {
                    int[] keys = constants.stream().mapToInt(IntEnumConstant::value).toArray();
                    Label[] labels = constants.stream().map(ignored -> new Label()).toArray(Label[]::new);
                    method.visitVarInsn(ILOAD, 2);
                    method.visitLookupSwitchInsn(unknownValue, keys, labels);
                    for (int i = 0; i < constants.size(); i++) {
                        method.visitLabel(labels[i]);
                        method.visitFieldInsn(
                                GETSTATIC,
                                Type.getInternalName(enumClass),
                                constants.get(i).field().getName(),
                                "L" + Type.getInternalName(enumClass) + ";");
                        method.visitInsn(ARETURN);
                    }
                }
                method.visitLabel(unknownValue);
                method.visitVarInsn(ILOAD, 2);
                invoke(method, unknown);
                method.visitInsn(ARETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                methodCount++;
            }
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
                        throw new UnsupportedSchemaException("Cannot access intEnum constant "
                                + enumClass.getName() + "." + field.getName());
                    }
                    if (seen.add(value)) {
                        result.add(new IntEnumConstant(field, value));
                    }
                }
            }
            // JVM lookupswitch keys must be sorted and unique.
            result.sort(Comparator.comparingInt(IntEnumConstant::value));
            return result;
        }

        private record IntEnumConstant(Field field, int value) {}

        private void emitAggregateMethods() {
            for (Schema schema : orderedAggregates) {
                if (schema.type() == ShapeType.MAP) {
                    emitMapWriter(schema);
                    emitMapReader(schema);
                } else {
                    emitListWriter(schema);
                    emitListReader(schema);
                }
            }
            emitWriteMapValueDispatch();
        }

        private void emitListWriter(Schema schema) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    aggregateWriterName(schema),
                    "(Ljava/util/List;L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
            method.visitVarInsn(ISTORE, 3);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ILOAD, 3);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedBeginArray", "(I)V", false);
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ISTORE, 4);
            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ILOAD, 4);
            method.visitVarInsn(ILOAD, 3);
            method.visitJumpInsn(IF_ICMPGE, done);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ILOAD, 4);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
            method.visitVarInsn(ASTORE, 5);
            Label nonNull = new Label();
            Label next = new Label();
            method.visitVarInsn(ALOAD, 5);
            method.visitJumpInsn(IFNONNULL, nonNull);
            emitWriteNull(method, 2);
            method.visitJumpInsn(GOTO, next);
            method.visitLabel(nonNull);
            emitWriteTarget(method, schema.listMember(), 5, 2);
            method.visitLabel(next);
            method.visitIincInsn(4, 1);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedEndArray", "()V", false);
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
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "containerSize", "()I", false);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "containerPreAllocationLimit", "()I", false);
            method.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false);
            method.visitVarInsn(ISTORE, 3);
            Label unknownSize = new Label();
            Label allocated = new Label();
            method.visitVarInsn(ILOAD, 3);
            method.visitLdcInsn(-1);
            method.visitJumpInsn(IF_ICMPEQ, unknownSize);
            method.visitTypeInsn(NEW, "java/util/ArrayList");
            method.visitInsn(DUP);
            method.visitVarInsn(ILOAD, 3);
            method.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(I)V", false);
            method.visitVarInsn(ASTORE, 2);
            method.visitJumpInsn(GOTO, allocated);
            method.visitLabel(unknownSize);
            method.visitTypeInsn(NEW, "java/util/ArrayList");
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
            method.visitVarInsn(ASTORE, 2);
            method.visitLabel(allocated);
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
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "size", "()I", true);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedBeginMap", "(I)V", false);
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            method.visitVarInsn(ALOAD, 1);
            method.visitTypeInsn(NEW, MAP_CONSUMER);
            method.visitInsn(DUP);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 2);
            method.visitLdcInsn(aggregateIds.get(target.id()));
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    MAP_CONSUMER,
                    "<init>",
                    "(L" + CODEC + ";L" + WRITER + ";I)V",
                    false);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map",
                    "forEach",
                    "(Ljava/util/function/BiConsumer;)V",
                    true);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedEndMap", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWriteMapValueDispatch() {
            List<Schema> maps = new ArrayList<>();
            for (Schema aggregate : orderedAggregates) {
                if (aggregate.type() == ShapeType.MAP) {
                    maps.add(aggregate);
                }
            }
            if (maps.isEmpty()) {
                return;
            }
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "writeMapValue",
                    "(ILjava/lang/Object;L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            int[] keys = new int[maps.size()];
            Label[] labels = new Label[maps.size()];
            for (int i = 0; i < maps.size(); i++) {
                keys[i] = aggregateIds.get(maps.get(i).id());
                labels[i] = new Label();
            }
            Label unknown = new Label();
            method.visitVarInsn(ILOAD, 1);
            method.visitLookupSwitchInsn(unknown, keys, labels);
            for (int i = 0; i < maps.size(); i++) {
                method.visitLabel(labels[i]);
                emitWriteTarget(method, maps.get(i).mapValueMember(), 2, 3);
                method.visitInsn(RETURN);
            }
            method.visitLabel(unknown);
            emitThrow(method, "Unknown map aggregate id");
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
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "containerSize", "()I", false);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "containerPreAllocationLimit", "()I", false);
            method.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false);
            method.visitVarInsn(ISTORE, 3);
            Label unknownSize = new Label();
            Label allocated = new Label();
            method.visitVarInsn(ILOAD, 3);
            method.visitLdcInsn(-1);
            method.visitJumpInsn(IF_ICMPEQ, unknownSize);
            method.visitVarInsn(ILOAD, 3);
            method.visitMethodInsn(
                    INVOKESTATIC,
                    "java/util/LinkedHashMap",
                    "newLinkedHashMap",
                    "(I)Ljava/util/LinkedHashMap;",
                    false);
            method.visitVarInsn(ASTORE, 2);
            method.visitJumpInsn(GOTO, allocated);
            method.visitLabel(unknownSize);
            method.visitTypeInsn(NEW, "java/util/LinkedHashMap");
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
            method.visitVarInsn(ASTORE, 2);
            method.visitLabel(allocated);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginObject", "()Z", false);
            Label done = new Label();
            Label loop = new Label();
            method.visitJumpInsn(IFEQ, done);
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    "generatedReadMapKey",
                    "()Ljava/lang/String;",
                    false);
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
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedBeginObject", "()V", false);
            List<RuntimeCodecPlan.MethodRange> chunks = structure.writerChunks();
            for (int chunk = 0; chunk < chunks.size(); chunk++) {
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
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedEndObject", "()V", false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;

            for (int chunk = 0; chunk < chunks.size(); chunk++) {
                emitWriterChunk(structure, chunk, chunks.get(chunk));
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
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedBeginObject", "()V", false);
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                Label next = new Label();
                method.visitVarInsn(ALOAD, 1);
                method.visitTypeInsn(INSTANCEOF, Type.getInternalName(member.unionVariant()));
                method.visitJumpInsn(IFEQ, next);
                emitField(method, member);
                Method accessor = member.unionAccessor();
                Class<?> valueType = accessor.getReturnType();
                method.visitVarInsn(ALOAD, 1);
                method.visitTypeInsn(CHECKCAST, Type.getInternalName(member.unionVariant()));
                invoke(method, accessor);
                method.visitVarInsn(storeOpcode(valueType), 3);
                emitWriteTarget(method, member.schema(), valueType, 3, 2);
                method.visitVarInsn(ALOAD, 2);
                method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedEndObject", "()V", false);
                method.visitInsn(RETURN);
                method.visitLabel(next);
            }
            emitThrow(method, "Unsupported or unknown union variant for " + structure.schema().id());
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWriterChunk(
                RuntimeCodecPlan.StructPlan structure,
                int chunk,
                RuntimeCodecPlan.MethodRange range
        ) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    writerChunkName(structure, chunk),
                    writerDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            for (int i = range.startInclusive(); i < range.endExclusive(); i++) {
                emitWriteMember(method, structure.members().get(i));
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
            emitField(method, member);
            emitWriteTarget(method, member.schema(), valueType, local, 2);
            method.visitLabel(done);
        }

        private void emitField(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            method.visitVarInsn(ALOAD, 2);
            method.visitFieldInsn(GETSTATIC, className, "E" + memberIds.get(member), "[B");
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "generatedWriteField", "([B)V", false);
        }

        private void emitWriteTarget(
                MethodVisitor method,
                Schema schema,
                Class<?> valueType,
                int valueLocal,
                int writerLocal
        ) {
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            switch (target.type()) {
                case STRUCTURE, UNION -> {
                    RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(target.id());
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(loadOpcode(valueType), valueLocal);
                    if (valueType == Object.class) {
                        method.visitTypeInsn(CHECKCAST, Type.getInternalName(nested.shapeClass()));
                    }
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
                default -> emitWriteScalar(method, target, valueType, valueLocal, writerLocal);
            }
        }

        private void emitWriteTarget(
                MethodVisitor method,
                Schema schema,
                int valueLocal,
                int writerLocal
        ) {
            emitWriteTarget(method, schema, Object.class, valueLocal, writerLocal);
        }

        private void emitWriteScalar(
                MethodVisitor method,
                Schema target,
                Class<?> valueType,
                int valueLocal,
                int writerLocal
        ) {
            method.visitVarInsn(ALOAD, writerLocal);
            method.visitInsn(ACONST_NULL);
            method.visitVarInsn(loadOpcode(valueType), valueLocal);
            ShapeType type = target.type();
            String descriptor;
            switch (type) {
                case BOOLEAN -> {
                    unbox(method, valueType, Boolean.class, "booleanValue", "()Z");
                    descriptor = "Z";
                }
                case BYTE -> {
                    unbox(method, valueType, Byte.class, "byteValue", "()B");
                    descriptor = "B";
                }
                case SHORT -> {
                    unbox(method, valueType, Short.class, "shortValue", "()S");
                    descriptor = "S";
                }
                case INTEGER -> {
                    unbox(method, valueType, Integer.class, "intValue", "()I");
                    descriptor = "I";
                }
                case LONG -> {
                    unbox(method, valueType, Long.class, "longValue", "()J");
                    descriptor = "J";
                }
                case FLOAT -> {
                    unbox(method, valueType, Float.class, "floatValue", "()F");
                    descriptor = "F";
                }
                case DOUBLE -> {
                    unbox(method, valueType, Double.class, "doubleValue", "()D");
                    descriptor = "D";
                }
                case ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                    method.visitMethodInsn(
                            INVOKEINTERFACE,
                            SMITHY_ENUM,
                            "getValue",
                            "()Ljava/lang/String;",
                            true);
                    descriptor = "Ljava/lang/String;";
                    type = ShapeType.STRING;
                }
                case INT_ENUM -> {
                    method.visitTypeInsn(CHECKCAST, SMITHY_INT_ENUM);
                    method.visitMethodInsn(INVOKEINTERFACE, SMITHY_INT_ENUM, "getValue", "()I", true);
                    descriptor = "I";
                    type = ShapeType.INTEGER;
                }
                case BIG_INTEGER -> {
                    castReference(method, valueType, "java/math/BigInteger");
                    descriptor = "Ljava/math/BigInteger;";
                }
                case BIG_DECIMAL -> {
                    castReference(method, valueType, "java/math/BigDecimal");
                    descriptor = "Ljava/math/BigDecimal;";
                }
                case STRING -> {
                    castReference(method, valueType, "java/lang/String");
                    descriptor = "Ljava/lang/String;";
                }
                case BLOB -> {
                    castReference(method, valueType, BYTE_BUFFER);
                    descriptor = "L" + BYTE_BUFFER + ";";
                }
                case TIMESTAMP -> {
                    castReference(method, valueType, Type.getInternalName(Instant.class));
                    descriptor = "L" + Type.getInternalName(Instant.class) + ";";
                }
                case DOCUMENT -> {
                    castReference(method, valueType, DOCUMENT);
                    descriptor = "L" + DOCUMENT + ";";
                }
                default -> throw new UnsupportedSchemaException("CBOR runtime codegen cannot write " + type);
            }
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    WRITER,
                    writerMethod(type),
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;" + descriptor + ")V",
                    false);
        }

        private static void castReference(MethodVisitor method, Class<?> actual, String target) {
            if (actual == Object.class || !Type.getInternalName(actual).equals(target)) {
                method.visitTypeInsn(CHECKCAST, target);
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

        private static void emitWriteNull(MethodVisitor method, int writerLocal) {
            method.visitVarInsn(ALOAD, writerLocal);
            method.visitInsn(ACONST_NULL);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    WRITER,
                    "writeNull",
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;)V",
                    false);
        }

        private void emitReader(RuntimeCodecPlan.StructPlan structure) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    readerName(structure),
                    readerDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            emitReaderBody(method, structure);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitReaderBuckets(RuntimeCodecPlan.StructPlan structure) {
            int buckets = structure.readerBuckets();
            if (buckets <= 1) {
                return;
            }
            for (int bucket = 0; bucket < buckets; bucket++) {
                emitReaderBucket(structure, bucket, buckets);
            }
        }

        private void emitReaderBody(MethodVisitor method, RuntimeCodecPlan.StructPlan structure) {
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginObject", "()Z", false);
            Label done = new Label();
            method.visitJumpInsn(IFEQ, done);
            Label loop = new Label();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                Label next = new Label();
                Label matched = new Label();
                emitTryReadField(method, member);
                method.visitJumpInsn(IFEQ, next);
                method.visitVarInsn(ALOAD, 1);
                // Dispatch skips null before resolving the member.
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
                method.visitJumpInsn(IFNE, matched);
                emitReadMember(method, member);
                method.visitLabel(matched);
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedObjectHasNext", "()Z", false);
                method.visitJumpInsn(IFEQ, done);
                method.visitLabel(next);
            }
            Label advance = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedReadFieldHash", "()I", false);
            method.visitVarInsn(ISTORE, 3);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
            method.visitJumpInsn(IFNE, advance);
            int buckets = structure.readerBuckets();
            if (buckets == 1) {
                emitReadDispatch(method, structure);
            } else {
                emitBucketDispatch(method, structure, buckets);
            }
            method.visitLabel(advance);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedObjectHasNext", "()Z", false);
            method.visitJumpInsn(IFNE, loop);
            method.visitLabel(done);
        }

        private void emitBucketDispatch(
                MethodVisitor method,
                RuntimeCodecPlan.StructPlan structure,
                int buckets
        ) {
            Map<Integer, Integer> hashBuckets = new LinkedHashMap<>();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                int hash = fieldHash(member.memberName());
                hashBuckets.put(hash, Math.floorMod(hash, buckets));
            }
            List<Integer> hashes = hashBuckets.keySet().stream().sorted().toList();
            int[] switchKeys = hashes.stream().mapToInt(Integer::intValue).toArray();
            Label unknown = new Label();
            Label dispatched = new Label();
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
            emitUnknownMember(method, structure);
            method.visitLabel(dispatched);
        }

        private void emitStructureValueReader(RuntimeCodecPlan.StructPlan structure) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    structureValueReaderName(structure),
                    structureValueReaderDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            invoke(method, structure.builderFactory());
            method.visitVarInsn(ASTORE, 2);
            emitReaderBody(method, structure);
            method.visitVarInsn(ALOAD, 2);
            invoke(method, findBuild(structure.builderClass(), structure.shapeClass()));
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
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
            method.visitInsn(ACONST_NULL);
            method.visitVarInsn(ASTORE, 3);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedBeginObject", "()Z", false);
            Label dispatch = new Label();
            method.visitJumpInsn(IFNE, dispatch);
            emitThrow(method, MISSING_UNION_MEMBER);

            Label advance = new Label();
            Label multiple = new Label();

            method.visitLabel(dispatch);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedReadFieldHash", "()I", false);
            method.visitVarInsn(ISTORE, 2);
            // Null fields do not count as union members.
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedTryReadNull", "()Z", false);
            method.visitJumpInsn(IFNE, advance);

            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new LinkedHashMap<>();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                groups.computeIfAbsent(fieldHash(member.memberName()), ignored -> new ArrayList<>()).add(member);
            }
            List<Integer> keys = groups.keySet().stream().sorted(Comparator.naturalOrder()).toList();
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
                    method.visitLdcInsn(member.memberName());
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedFieldEquals",
                            "([BLjava/lang/String;)Z",
                            false);
                    method.visitJumpInsn(IFEQ, next);
                    emitUnionValue(method, member, advance, multiple);
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
            method.visitVarInsn(ASTORE, 4);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipValue", "()V", false);
            method.visitVarInsn(ALOAD, 3);
            method.visitJumpInsn(IFNONNULL, multiple);
            Class<?> unknownClass = findUnknownUnionClass(structure.shapeClass());
            method.visitTypeInsn(NEW, Type.getInternalName(unknownClass));
            method.visitInsn(DUP);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    Type.getInternalName(unknownClass),
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false);
            method.visitVarInsn(ASTORE, 3);
            method.visitJumpInsn(GOTO, advance);

            method.visitLabel(multiple);
            emitThrow(method, "Union object contains multiple members");

            method.visitLabel(advance);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedObjectHasNext", "()Z", false);
            method.visitJumpInsn(IFNE, dispatch);
            method.visitVarInsn(ALOAD, 3);
            Label found = new Label();
            method.visitJumpInsn(IFNONNULL, found);
            emitThrow(method, MISSING_UNION_MEMBER);
            method.visitLabel(found);
            method.visitVarInsn(ALOAD, 3);
            // Required by the verifier after merging variant types.
            method.visitTypeInsn(CHECKCAST, unionName);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitUnionValue(
                MethodVisitor method,
                RuntimeCodecPlan.MemberPlan member,
                Label advance,
                Label multiple
        ) {
            method.visitVarInsn(ALOAD, 3);
            method.visitJumpInsn(IFNONNULL, multiple);
            String variant = Type.getInternalName(member.unionVariant());
            method.visitTypeInsn(NEW, variant);
            method.visitInsn(DUP);
            Class<?> parameter = member.unionAccessor().getReturnType();
            emitReadTarget(method, member.schema(), 1, parameter);
            if (!parameter.isPrimitive()) {
                adaptObject(method, parameter);
            }
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    variant,
                    "<init>",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(member.unionAccessor().getReturnType())),
                    false);
            method.visitVarInsn(ASTORE, 3);
            method.visitJumpInsn(GOTO, advance);
        }

        private static Class<?> findUnknownUnionClass(Class<?> unionClass) {
            for (Class<?> nested : unionClass.getDeclaredClasses()) {
                if (nested.getSimpleName().equals("$Unknown")) {
                    return nested;
                }
            }
            throw new UnsupportedSchemaException("No unknown union variant on " + unionClass.getName());
        }

        private static Method findUnknownUnionSetter(Class<?> builderClass) {
            for (Method method : builderClass.getMethods()) {
                if (method.getName().equals("$unknownMember")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {
                    return method;
                }
            }
            throw new UnsupportedSchemaException("No unknown union setter on " + builderClass.getName());
        }

        private void emitTryReadField(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            byte[] encoded = member.memberName().getBytes(StandardCharsets.UTF_8);
            method.visitVarInsn(ALOAD, 1);
            if (encoded.length <= 2 * Long.BYTES) {
                method.visitLdcInsn(packName(encoded, 0, Math.min(encoded.length, Long.BYTES)));
                method.visitLdcInsn(packName(
                        encoded,
                        Math.max(0, encoded.length - Long.BYTES),
                        encoded.length));
                method.visitLdcInsn(encoded.length);
                method.visitLdcInsn(member.memberName());
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        "generatedTryReadField",
                        "(JJILjava/lang/String;)Z",
                        false);
                return;
            }
            method.visitFieldInsn(GETSTATIC, className, "N" + memberIds.get(member), "[B");
            method.visitLdcInsn(member.memberName());
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    "generatedTryReadField",
                    "([BLjava/lang/String;)Z",
                    false);
        }

        private static long packName(byte[] value, int start, int end) {
            long result = 0;
            for (int i = start; i < end; i++) {
                result = (result << Byte.SIZE) | (value[i] & 0xffL);
            }
            return result;
        }

        private void emitReadDispatch(MethodVisitor method, RuntimeCodecPlan.StructPlan structure) {
            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new LinkedHashMap<>();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
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
                    method.visitLdcInsn(member.memberName());
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedFieldEquals",
                            "([BLjava/lang/String;)Z",
                            false);
                    method.visitJumpInsn(IFEQ, next);
                    emitReadMember(method, member);
                    method.visitJumpInsn(GOTO, after);
                    method.visitLabel(next);
                }
                method.visitJumpInsn(GOTO, unknown);
            }
            method.visitLabel(unknown);
            emitUnknownMember(method, structure);
            method.visitLabel(after);
        }

        private void emitReaderBucket(
                RuntimeCodecPlan.StructPlan structure,
                int bucket,
                int bucketCount
        ) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PRIVATE,
                    readerBucketName(structure, bucket),
                    readerBucketDescriptor(structure),
                    null,
                    null);
            method.visitCode();
            Map<Integer, List<RuntimeCodecPlan.MemberPlan>> groups = new LinkedHashMap<>();
            structure.members()
                    .stream()
                    .filter(member -> Math.floorMod(fieldHash(member.memberName()), bucketCount) == bucket)
                    .forEach(member -> groups.computeIfAbsent(
                            fieldHash(member.memberName()),
                            ignored -> new ArrayList<>()).add(member));
            List<Integer> keys = groups.keySet().stream().sorted().toList();
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
                    method.visitLdcInsn(member.memberName());
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            READER,
                            "generatedFieldEquals",
                            "([BLjava/lang/String;)Z",
                            false);
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

        private void emitUnknownMember(MethodVisitor method, RuntimeCodecPlan.StructPlan structure) {
            if (structure.union()) {
                method.visitVarInsn(ALOAD, 2);
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        READER,
                        "generatedFieldName",
                        "()Ljava/lang/String;",
                        false);
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipValue", "()V", false);
                invoke(method, findUnknownUnionSetter(structure.builderClass()));
                method.visitInsn(POP);
            } else {
                method.visitVarInsn(ALOAD, 1);
                method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedSkipValue", "()V", false);
            }
        }

        private void emitReadMember(MethodVisitor method, RuntimeCodecPlan.MemberPlan member) {
            method.visitVarInsn(ALOAD, 2);
            Class<?> parameter = member.setter().getParameterTypes()[0];
            emitReadTarget(method, member.schema(), 1, parameter);
            if (!parameter.isPrimitive()) {
                adaptObject(method, parameter);
            }
            invoke(method, member.setter());
            if (member.setter().getReturnType() != void.class) {
                method.visitInsn(POP);
            }
        }

        private void emitReadTarget(MethodVisitor method, Schema schema, int readerLocal) {
            emitReadTarget(method, schema, readerLocal, Object.class);
        }

        private void emitReadTarget(
                MethodVisitor method,
                Schema schema,
                int readerLocal,
                Class<?> parameter
        ) {
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            switch (target.type()) {
                case BOOLEAN -> {
                    emitReadScalar(method, readerLocal, "readBoolean", "Z");
                    box(method, parameter, Boolean.class, "(Z)Ljava/lang/Boolean;");
                }
                case BYTE -> {
                    emitReadScalar(method, readerLocal, "readByte", "B");
                    box(method, parameter, Byte.class, "(B)Ljava/lang/Byte;");
                }
                case SHORT -> {
                    emitReadScalar(method, readerLocal, "readShort", "S");
                    box(method, parameter, Short.class, "(S)Ljava/lang/Short;");
                }
                case INTEGER -> {
                    emitReadScalar(method, readerLocal, "readInteger", "I");
                    box(method, parameter, Integer.class, "(I)Ljava/lang/Integer;");
                }
                case LONG -> {
                    emitReadScalar(method, readerLocal, "readLong", "J");
                    box(method, parameter, Long.class, "(J)Ljava/lang/Long;");
                }
                case FLOAT -> {
                    emitReadScalar(method, readerLocal, "readFloat", "F");
                    box(method, parameter, Float.class, "(F)Ljava/lang/Float;");
                }
                case DOUBLE -> {
                    emitReadScalar(method, readerLocal, "readDouble", "D");
                    box(method, parameter, Double.class, "(D)Ljava/lang/Double;");
                }
                case BIG_INTEGER -> emitReadObject(method, readerLocal, "readBigInteger", "Ljava/math/BigInteger;");
                case BIG_DECIMAL -> emitReadObject(method, readerLocal, "readBigDecimal", "Ljava/math/BigDecimal;");
                case STRING -> emitReadObject(method, readerLocal, "readString", "Ljava/lang/String;");
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
                case INT_ENUM -> {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, readerLocal);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            intEnumReaderName(target.shapeClass()),
                            "(L" + READER + ";)L" + Type.getInternalName(target.shapeClass()) + ";",
                            false);
                }
                case BLOB -> emitReadObject(method, readerLocal, "readBlob", "L" + BYTE_BUFFER + ";");
                case TIMESTAMP -> emitReadObject(
                        method,
                        readerLocal,
                        "readTimestamp",
                        "L" + Type.getInternalName(Instant.class) + ";");
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
                case STRUCTURE, UNION -> emitReadStructureValue(method, target, readerLocal);
                default -> throw new UnsupportedSchemaException(
                        "CBOR runtime codegen cannot read " + target.type() + " at " + target.id());
            }
        }

        private static void emitReadScalar(
                MethodVisitor method,
                int readerLocal,
                String name,
                String descriptor
        ) {
            method.visitVarInsn(ALOAD, readerLocal);
            method.visitInsn(ACONST_NULL);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    READER,
                    name,
                    "(Lsoftware/amazon/smithy/java/core/schema/Schema;)" + descriptor,
                    false);
        }

        private static void emitReadObject(
                MethodVisitor method,
                int readerLocal,
                String name,
                String descriptor
        ) {
            emitReadScalar(method, readerLocal, name, descriptor);
        }

        private void emitReadStructureValue(MethodVisitor method, Schema schema, int readerLocal) {
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            RuntimeCodecPlan.StructPlan nested = structuresBySchema.get(target.id());
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, readerLocal);
            // Some generated unions use STRUCTURE schemas.
            if (nested.union()) {
                method.visitMethodInsn(
                        INVOKESPECIAL,
                        className,
                        unionValueReaderName(nested),
                        "(L" + READER + ";)L" + Type.getInternalName(nested.shapeClass()) + ";",
                        false);
                return;
            }
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    structureValueReaderName(nested),
                    structureValueReaderDescriptor(nested),
                    false);
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

        private static void adaptObject(MethodVisitor method, Class<?> target) {
            if (target.isPrimitive()) {
                if (target == boolean.class) {
                    unbox(method, Object.class, Boolean.class, "booleanValue", "()Z");
                } else if (target == byte.class) {
                    unbox(method, Object.class, Byte.class, "byteValue", "()B");
                } else if (target == short.class) {
                    unbox(method, Object.class, Short.class, "shortValue", "()S");
                } else if (target == int.class) {
                    unbox(method, Object.class, Integer.class, "intValue", "()I");
                } else if (target == long.class) {
                    unbox(method, Object.class, Long.class, "longValue", "()J");
                } else if (target == float.class) {
                    unbox(method, Object.class, Float.class, "floatValue", "()F");
                } else if (target == double.class) {
                    unbox(method, Object.class, Double.class, "doubleValue", "()D");
                }
            } else if (target != Object.class) {
                method.visitTypeInsn(CHECKCAST, Type.getInternalName(target));
            }
        }

        private void emitWriteEntry() {
            RuntimeCodecPlan.StructPlan root = plan.rootStructure();
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
                    writerName(root),
                    writerDescriptor(root),
                    false);
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitReadEntry() {
            emitReadEntry("[B", "([BL" + SETTINGS + ";)V");
            emitReadEntry("L" + BYTE_BUFFER + ";", "(L" + BYTE_BUFFER + ";L" + SETTINGS + ";)V");
        }

        private void emitReadEntry(String sourceDescriptor, String readerConstructorDescriptor) {
            RuntimeCodecPlan.StructPlan root = plan.rootStructure();
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "read",
                    "(" + sourceDescriptor + "L" + SHAPE_BUILDER + ";L" + SETTINGS
                            + ";)L" + SERIALIZABLE_SHAPE + ";",
                    null,
                    null);
            method.visitCode();
            method.visitTypeInsn(NEW, READER);
            method.visitInsn(DUP);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    READER,
                    "<init>",
                    readerConstructorDescriptor,
                    false);
            method.visitVarInsn(ASTORE, 4);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 4);
            method.visitVarInsn(ALOAD, 2);
            method.visitTypeInsn(CHECKCAST, Type.getInternalName(root.builderClass()));
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    readerName(root),
                    readerDescriptor(root),
                    false);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(INVOKEVIRTUAL, READER, "generatedFinishRoot", "()V", false);
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
                case DOCUMENT -> "writeDocument";
                default -> throw new UnsupportedSchemaException("CBOR runtime codegen has no writer for " + type);
            };
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

        private static String readerDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + READER + ";L" + Type.getInternalName(structure.builderClass()) + ";)V";
        }

        private static String readerBucketDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + READER + ";L" + Type.getInternalName(structure.builderClass()) + ";I)Z";
        }

        private String structureValueReaderName(RuntimeCodecPlan.StructPlan structure) {
            return "readV" + structureIds.get(structure);
        }

        private static String structureValueReaderDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + READER + ";)L" + Type.getInternalName(structure.shapeClass()) + ";";
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

        private String intEnumReaderName(Class<?> enumClass) {
            return "readI" + intEnumIds.get(enumClass);
        }

        private String enumReaderName(Class<?> enumClass) {
            return "readE" + enumIds.get(enumClass);
        }

    }
}
