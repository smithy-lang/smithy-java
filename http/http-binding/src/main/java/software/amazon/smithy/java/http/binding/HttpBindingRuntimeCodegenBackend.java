/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.emitDefaultConstructor;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.invoke;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeType;

final class HttpBindingRuntimeCodegenBackend implements RuntimeCodecBackend<HttpBindingWriter>, Opcodes {

    private static final Budgets BUDGETS =
            new Budgets(Integer.MAX_VALUE, Integer.MAX_VALUE, 8, Integer.MAX_VALUE);
    private static final String WRITER = Type.getInternalName(HttpBindingWriter.class);
    private static final String SINK = Type.getInternalName(HttpBindingSerializer.class);
    private static final String STRUCT = Type.getInternalName(SerializableStruct.class);
    private static final String SMITHY_ENUM = Type.getInternalName(SmithyEnum.class);
    private static final String SMITHY_INT_ENUM = Type.getInternalName(SmithyIntEnum.class);
    private static final String BYTE_BUFFER = Type.getInternalName(ByteBuffer.class);
    private static final String INSTANT = Type.getInternalName(Instant.class);
    private static final String FORMATTER = Type.getInternalName(TimestampFormatter.class);
    private static final String PRELUDE = Type.getInternalName(TimestampFormatter.Prelude.class);
    private static final String HEADER_NAME = Type.getInternalName(HeaderName.class);
    private static final String BUFFER_UTILS = Type.getInternalName(ByteBufferUtils.class);
    private static final String BASE64 = Type.getInternalName(Base64.class);
    private static final String BASE64_ENCODER = Type.getInternalName(Base64.Encoder.class);
    private static final String STRING = "java/lang/String";
    private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";
    private static final String BIND_DESCRIPTOR = "(Ljava/lang/String;Ljava/lang/String;)V";
    private static final String WRITE_DESCRIPTOR = "(L" + STRUCT + ";L" + SINK + ";)V";

    private static final int SHAPE_LOCAL = 3;
    private static final int VALUE_LOCAL = 4;
    private static final int ITERATOR_LOCAL = 5;
    private static final int ELEMENT_LOCAL = 6;

    private final boolean isResponse;

    HttpBindingRuntimeCodegenBackend(boolean isResponse) {
        this.isResponse = isResponse;
    }

    @Override
    public String id() {
        return HttpBindingCodegen.ID;
    }

    @Override
    public String variant() {
        return isResponse ? "Response" : "Request";
    }

    @Override
    public Class<HttpBindingWriter> codecType() {
        return HttpBindingWriter.class;
    }

    @Override
    public Class<?> lookupHost() {
        return HttpBindingWriter.class;
    }

    @Override
    public Mode mode() {
        return Mode.WRITE_ONLY;
    }

    @Override
    public Budgets budgets() {
        return BUDGETS;
    }

    @Override
    public MemberSelector memberSelector() {
        return HttpBindingCodegen.Selector.of(isResponse);
    }

    @Override
    public Emission emit(RuntimeCodecPlan plan, String generatedName) {
        return new Generator(plan, generatedName, isResponse).generate();
    }

    private static final class Generator implements Opcodes {
        private final RuntimeCodecPlan plan;
        private final String className;
        private final boolean isResponse;
        private final List<RuntimeCodecPlan.MemberPlan> members;
        private final HttpBindingSchemaExtensions.Binding[] bindings;
        private final HttpBindingSchemaExtensions.MemberBinding[] memberBindings;
        private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        private int methodCount;

        Generator(RuntimeCodecPlan plan, String className, boolean isResponse) {
            this.plan = plan;
            this.className = className;
            this.isResponse = isResponse;
            RuntimeCodecPlan.StructPlan root = plan.rootStructure();
            if (root.union()) {
                throw new UnsupportedSchemaException("Cannot bind a union to HTTP: " + plan.root().id());
            }
            this.members = root.members();
            this.bindings = HttpBindingCodegen.bindings(plan.root(), isResponse);
            this.memberBindings = HttpBindingCodegen.memberBindings(plan.root(), isResponse);
        }

        Emission generate() {
            writer.visit(V17, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Object", new String[] {WRITER});
            emitFields();
            emitDefaultConstructor(writer);
            methodCount++;
            emitClassInitializer();
            emitWrite();
            writer.visitEnd();
            return new Emission(writer.toByteArray(), methodCount);
        }

        private void emitFields() {
            for (int i = 0; i < members.size(); i++) {
                if (kindOf(i) == HttpBindingSchemaExtensions.Binding.HEADER) {
                    writer.visitField(
                            ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                            "H" + i,
                            STRING_DESCRIPTOR,
                            null,
                            null).visitEnd();
                }
            }
        }

        private void emitClassInitializer() {
            boolean any = false;
            for (int i = 0; i < members.size(); i++) {
                if (kindOf(i) == HttpBindingSchemaExtensions.Binding.HEADER) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return;
            }
            MethodVisitor method = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            method.visitCode();
            for (int i = 0; i < members.size(); i++) {
                if (kindOf(i) != HttpBindingSchemaExtensions.Binding.HEADER) {
                    continue;
                }
                // Preserve canonical header-name identity.
                method.visitLdcInsn(headerName(i));
                method.visitMethodInsn(
                        INVOKESTATIC,
                        HEADER_NAME,
                        "canonicalize",
                        "(Ljava/lang/String;)Ljava/lang/String;",
                        false);
                method.visitFieldInsn(PUTSTATIC, className, "H" + i, STRING_DESCRIPTOR);
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        private void emitWrite() {
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "write", WRITE_DESCRIPTOR, null, null);
            method.visitCode();
            if (!members.isEmpty()) {
                method.visitVarInsn(ALOAD, 1);
                method.visitTypeInsn(CHECKCAST, Type.getInternalName(plan.rootStructure().shapeClass()));
                method.visitVarInsn(ASTORE, SHAPE_LOCAL);
                List<RuntimeCodecPlan.MethodRange> chunks = plan.rootStructure().writerChunks();
                for (int chunk = 0; chunk < chunks.size(); chunk++) {
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, SHAPE_LOCAL);
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            writeChunkName(chunk),
                            writeChunkDescriptor(),
                            false);
                }
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;

            List<RuntimeCodecPlan.MethodRange> chunks = plan.rootStructure().writerChunks();
            for (int chunk = 0; chunk < chunks.size() && !members.isEmpty(); chunk++) {
                RuntimeCodecPlan.MethodRange range = chunks.get(chunk);
                MethodVisitor chunkMethod = writer.visitMethod(
                        ACC_PRIVATE,
                        writeChunkName(chunk),
                        writeChunkDescriptor(),
                        null,
                        null);
                chunkMethod.visitCode();
                chunkMethod.visitVarInsn(ALOAD, 1);
                chunkMethod.visitVarInsn(ASTORE, SHAPE_LOCAL);
                for (int i = range.startInclusive(); i < range.endExclusive(); i++) {
                    emitMember(chunkMethod, i);
                }
                chunkMethod.visitInsn(RETURN);
                chunkMethod.visitMaxs(0, 0);
                chunkMethod.visitEnd();
                methodCount++;
            }
        }

        private static String writeChunkName(int chunk) {
            return "writeChunk" + chunk;
        }

        private String writeChunkDescriptor() {
            return "(L"
                    + Type.getInternalName(plan.rootStructure().shapeClass())
                    + ";L"
                    + SINK
                    + ";)V";
        }

        private void emitMember(MethodVisitor method, int index) {
            RuntimeCodecPlan.MemberPlan member = members.get(index);
            Label skip = new Label();

            if (member.presence() != null) {
                method.visitVarInsn(ALOAD, SHAPE_LOCAL);
                invoke(method, member.presence());
                method.visitJumpInsn(IFEQ, skip);
            }

            Class<?> javaType = member.getter().getReturnType();
            if (javaType.isPrimitive()) {
                emitBind(method, index, javaType, () -> {
                    method.visitVarInsn(ALOAD, SHAPE_LOCAL);
                    invoke(method, member.getter());
                });
                method.visitLabel(skip);
                return;
            }

            method.visitVarInsn(ALOAD, SHAPE_LOCAL);
            invoke(method, member.getter());
            method.visitVarInsn(ASTORE, VALUE_LOCAL);
            method.visitVarInsn(ALOAD, VALUE_LOCAL);
            method.visitJumpInsn(IFNULL, skip);

            ShapeType targetType = member.target().type();
            var binding = kindOf(index);
            if ((targetType == ShapeType.LIST || targetType == ShapeType.SET)
                    && (binding == HttpBindingSchemaExtensions.Binding.HEADER
                            || binding == HttpBindingSchemaExtensions.Binding.QUERY)) {
                emitListMember(method, index);
            } else {
                emitBind(method, index, javaType, () -> method.visitVarInsn(ALOAD, VALUE_LOCAL));
            }
            method.visitLabel(skip);
        }

        private void emitListMember(MethodVisitor method, int index) {
            RuntimeCodecPlan.MemberPlan member = members.get(index);
            Schema list = member.target();
            boolean sparse = list.hasTrait(TraitKey.SPARSE_TRAIT);
            method.visitVarInsn(ALOAD, VALUE_LOCAL);
            method.visitTypeInsn(CHECKCAST, "java/util/List");
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
            method.visitVarInsn(ASTORE, ITERATOR_LOCAL);
            Label loop = new Label();
            Label end = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, ITERATOR_LOCAL);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
            method.visitJumpInsn(IFEQ, end);
            method.visitVarInsn(ALOAD, ITERATOR_LOCAL);
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
            method.visitVarInsn(ASTORE, ELEMENT_LOCAL);
            Label next = new Label();
            if (sparse) {
                method.visitVarInsn(ALOAD, ELEMENT_LOCAL);
                Label nonNull = new Label();
                method.visitJumpInsn(IFNONNULL, nonNull);
                method.visitVarInsn(ALOAD, 2);
                method.visitMethodInsn(INVOKEVIRTUAL, SINK, "bindNull", "()V", false);
                method.visitJumpInsn(GOTO, next);
                method.visitLabel(nonNull);
            }
            emitBindValue(
                    method,
                    index,
                    list.listMember().memberTarget(),
                    Object.class,
                    () -> method.visitVarInsn(ALOAD, ELEMENT_LOCAL));
            method.visitLabel(next);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(end);
        }

        private void emitBind(MethodVisitor method, int index, Class<?> javaType, Runnable loadValue) {
            emitBindValue(method, index, members.get(index).target(), javaType, loadValue);
        }

        private void emitBindValue(
                MethodVisitor method,
                int index,
                Schema target,
                Class<?> javaType,
                Runnable loadValue
        ) {
            var kind = kindOf(index);
            switch (kind) {
                case HEADER -> {
                    method.visitVarInsn(ALOAD, 2);
                    method.visitFieldInsn(GETSTATIC, className, "H" + index, STRING_DESCRIPTOR);
                    boolean trusted = emitStringValue(method, index, target, javaType, loadValue, true);
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            SINK,
                            trusted ? "bindHeaderTrusted" : "bindHeader",
                            BIND_DESCRIPTOR,
                            false);
                }
                case QUERY -> {
                    method.visitVarInsn(ALOAD, 2);
                    method.visitLdcInsn(wireName(index));
                    emitStringValue(method, index, target, javaType, loadValue, false);
                    method.visitMethodInsn(INVOKEVIRTUAL, SINK, "bindQuery", BIND_DESCRIPTOR, false);
                }
                case PREFIX_HEADERS -> {
                    requireTarget(index, target, ShapeType.MAP);
                    method.visitVarInsn(ALOAD, 2);
                    method.visitLdcInsn(wireName(index));
                    loadValue.run();
                    checkCast(method, javaType, Type.getInternalName(Map.class));
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            SINK,
                            "bindPrefixHeaders",
                            "(Ljava/lang/String;Ljava/util/Map;)V",
                            false);
                }
                case QUERY_PARAMS -> {
                    requireTarget(index, target, ShapeType.MAP);
                    method.visitVarInsn(ALOAD, 2);
                    loadValue.run();
                    checkCast(method, javaType, Type.getInternalName(Map.class));
                    method.visitMethodInsn(
                            INVOKEVIRTUAL,
                            SINK,
                            "bindQueryParams",
                            "(Ljava/util/Map;)V",
                            false);
                }
                case PAYLOAD -> emitPayloadValue(method, index, target, javaType, loadValue);
                case STATUS -> {
                    method.visitVarInsn(ALOAD, 2);
                    emitIntValue(method, index, target, javaType, loadValue);
                    method.visitMethodInsn(INVOKEVIRTUAL, SINK, "bindStatus", "(I)V", false);
                }
                default -> throw new UnsupportedSchemaException(
                        "HTTP binding runtime codegen cannot write " + kind + " at "
                                + members.get(index).schema().id());
            }
        }

        private void emitPayloadValue(
                MethodVisitor method,
                int index,
                Schema target,
                Class<?> javaType,
                Runnable loadValue
        ) {
            method.visitVarInsn(ALOAD, 2);

            if (DataStream.class.isAssignableFrom(javaType)) {
                loadReference(method, javaType, loadValue, DataStream.class);
                invokePayload(method, Type.getDescriptor(DataStream.class));
                return;
            }
            if (EventStream.class.isAssignableFrom(javaType)) {
                loadReference(method, javaType, loadValue, EventStream.class);
                invokePayload(method, Type.getDescriptor(EventStream.class));
                return;
            }

            switch (target.type()) {
                case STRING -> {
                    loadReference(method, javaType, loadValue, String.class);
                    invokePayload(method, Type.getDescriptor(String.class));
                }
                case ENUM -> {
                    loadValue.run();
                    method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                    method.visitMethodInsn(
                            INVOKEINTERFACE,
                            SMITHY_ENUM,
                            "getValue",
                            "()Ljava/lang/String;",
                            true);
                    invokePayload(method, Type.getDescriptor(String.class));
                }
                case BLOB -> {
                    if (javaType == byte[].class) {
                        loadReference(method, javaType, loadValue, byte[].class);
                        invokePayload(method, Type.getDescriptor(byte[].class));
                    } else {
                        loadReference(method, javaType, loadValue, ByteBuffer.class);
                        invokePayload(method, Type.getDescriptor(ByteBuffer.class));
                    }
                }
                case TIMESTAMP -> {
                    loadReference(method, javaType, loadValue, Instant.class);
                    invokePayload(method, Type.getDescriptor(Instant.class));
                }
                case STRUCTURE, UNION -> {
                    loadReference(method, javaType, loadValue, SerializableStruct.class);
                    invokePayload(method, Type.getDescriptor(SerializableStruct.class));
                }
                case DOCUMENT -> {
                    loadReference(method, javaType, loadValue, Document.class);
                    invokePayload(method, Type.getDescriptor(Document.class));
                }
                default -> throw new UnsupportedSchemaException(
                        "HTTP payload runtime codegen cannot write "
                                + target.type()
                                + " at "
                                + members.get(index).schema().id());
            }
        }

        private void loadReference(
                MethodVisitor method,
                Class<?> javaType,
                Runnable loadValue,
                Class<?> expectedType
        ) {
            loadValue.run();
            checkCast(method, javaType, Type.getInternalName(expectedType));
        }

        private void invokePayload(MethodVisitor method, String valueDescriptor) {
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    SINK,
                    "bindPayload",
                    "(" + valueDescriptor + ")V",
                    false);
        }

        private void requireTarget(int index, Schema target, ShapeType expected) {
            if (target.type() != expected) {
                throw new UnsupportedSchemaException(
                        kindOf(index) + " must target " + expected + " at " + members.get(index).schema().id());
            }
        }

        private boolean emitStringValue(
                MethodVisitor method,
                int index,
                Schema target,
                Class<?> javaType,
                Runnable loadValue,
                boolean isHeader
        ) {
            switch (target.type()) {
                case BOOLEAN -> {
                    emitToString(method, javaType, loadValue, Boolean.class, boolean.class, "(Z)");
                    return true;
                }
                case BYTE -> {
                    emitToString(method, javaType, loadValue, Byte.class, byte.class, "(B)");
                    return true;
                }
                case SHORT -> {
                    emitToString(method, javaType, loadValue, Short.class, short.class, "(S)");
                    return true;
                }
                case INTEGER -> {
                    emitToString(method, javaType, loadValue, Integer.class, int.class, "(I)");
                    return true;
                }
                case LONG -> {
                    emitToString(method, javaType, loadValue, Long.class, long.class, "(J)");
                    return true;
                }
                case FLOAT -> {
                    emitToString(method, javaType, loadValue, Float.class, float.class, "(F)");
                    return true;
                }
                case DOUBLE -> {
                    emitToString(method, javaType, loadValue, Double.class, double.class, "(D)");
                    return true;
                }
                case BIG_INTEGER -> {
                    emitInstanceToString(method, javaType, loadValue, BigInteger.class);
                    return true;
                }
                case BIG_DECIMAL -> {
                    emitInstanceToString(method, javaType, loadValue, BigDecimal.class);
                    return true;
                }
                case STRING -> {
                    boolean base64 = isHeader && hasMediaType(index);
                    if (base64) {
                        emitBase64Encoder(method);
                    }
                    loadValue.run();
                    checkCast(method, javaType, STRING);
                    if (base64) {
                        emitBase64OfString(method);
                        return true;
                    }
                    return false;
                }
                case ENUM -> {
                    boolean base64 = isHeader && hasMediaType(index);
                    if (base64) {
                        emitBase64Encoder(method);
                    }
                    loadValue.run();
                    method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                    method.visitMethodInsn(
                            INVOKEINTERFACE,
                            SMITHY_ENUM,
                            "getValue",
                            "()Ljava/lang/String;",
                            true);
                    if (base64) {
                        emitBase64OfString(method);
                        return true;
                    }
                    // An unknown enum variant carries whatever string the caller put in it.
                    return false;
                }
                case INT_ENUM -> {
                    loadValue.run();
                    method.visitTypeInsn(CHECKCAST, SMITHY_INT_ENUM);
                    method.visitMethodInsn(INVOKEINTERFACE, SMITHY_INT_ENUM, "getValue", "()I", true);
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            "java/lang/Integer",
                            "toString",
                            "(I)Ljava/lang/String;",
                            false);
                    return true;
                }
                case BLOB -> {
                    loadValue.run();
                    checkCast(method, javaType, BYTE_BUFFER);
                    method.visitMethodInsn(
                            INVOKESTATIC,
                            BUFFER_UTILS,
                            "base64Encode",
                            "(L" + BYTE_BUFFER + ";)Ljava/lang/String;",
                            false);
                    return true;
                }
                case TIMESTAMP -> {
                    emitFormatterConstant(method, index);
                    loadValue.run();
                    checkCast(method, javaType, INSTANT);
                    method.visitMethodInsn(
                            INVOKEINTERFACE,
                            FORMATTER,
                            "writeString",
                            "(L" + INSTANT + ";)Ljava/lang/String;",
                            true);
                    return true;
                }
                default -> throw new UnsupportedSchemaException(
                        "HTTP binding runtime codegen cannot write "
                                + target.type()
                                + " at "
                                + members.get(index).schema().id());
            }
        }

        private void emitIntValue(
                MethodVisitor method,
                int index,
                Schema target,
                Class<?> javaType,
                Runnable loadValue
        ) {
            String boxed;
            String unboxer;
            String descriptor;
            switch (target.type()) {
                case BYTE -> {
                    boxed = "java/lang/Byte";
                    unboxer = "byteValue";
                    descriptor = "()B";
                }
                case SHORT -> {
                    boxed = "java/lang/Short";
                    unboxer = "shortValue";
                    descriptor = "()S";
                }
                case INTEGER -> {
                    boxed = "java/lang/Integer";
                    unboxer = "intValue";
                    descriptor = "()I";
                }
                default -> throw new UnsupportedSchemaException(
                        "An HTTP response code cannot be a "
                                + target.type()
                                + " at "
                                + members.get(index).schema().id());
            }
            loadValue.run();
            if (!javaType.isPrimitive()) {
                method.visitTypeInsn(CHECKCAST, boxed);
                method.visitMethodInsn(INVOKEVIRTUAL, boxed, unboxer, descriptor, false);
            }
        }

        private void emitToString(
                MethodVisitor method,
                Class<?> javaType,
                Runnable loadValue,
                Class<?> boxedType,
                Class<?> primitiveType,
                String argumentDescriptor
        ) {
            loadValue.run();
            String boxed = Type.getInternalName(boxedType);
            if (javaType.isPrimitive()) {
                if (javaType != primitiveType) {
                    throw new UnsupportedSchemaException(
                            "Expected a " + primitiveType + " accessor but found " + javaType);
                }
                method.visitMethodInsn(
                        INVOKESTATIC,
                        boxed,
                        "toString",
                        argumentDescriptor + "Ljava/lang/String;",
                        false);
            } else {
                checkCast(method, javaType, boxed);
                method.visitMethodInsn(INVOKEVIRTUAL, boxed, "toString", "()Ljava/lang/String;", false);
            }
        }

        private void emitInstanceToString(
                MethodVisitor method,
                Class<?> javaType,
                Runnable loadValue,
                Class<?> type
        ) {
            loadValue.run();
            String owner = Type.getInternalName(type);
            checkCast(method, javaType, owner);
            method.visitMethodInsn(INVOKEVIRTUAL, owner, "toString", "()Ljava/lang/String;", false);
        }

        private void emitBase64Encoder(MethodVisitor method) {
            method.visitMethodInsn(INVOKESTATIC, BASE64, "getEncoder", "()L" + BASE64_ENCODER + ";", false);
        }

        private void emitBase64OfString(MethodVisitor method) {
            method.visitFieldInsn(
                    GETSTATIC,
                    Type.getInternalName(StandardCharsets.class),
                    "UTF_8",
                    "Ljava/nio/charset/Charset;");
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    STRING,
                    "getBytes",
                    "(Ljava/nio/charset/Charset;)[B",
                    false);
            method.visitMethodInsn(
                    INVOKEVIRTUAL,
                    BASE64_ENCODER,
                    "encodeToString",
                    "([B)Ljava/lang/String;",
                    false);
        }

        private void emitFormatterConstant(MethodVisitor method, int index) {
            TimestampFormatter formatter = memberBinding(index).timestampFormatter();
            TimestampFormatter.Prelude prelude = null;
            for (TimestampFormatter.Prelude candidate : TimestampFormatter.Prelude.values()) {
                if (candidate == formatter) {
                    prelude = candidate;
                    break;
                }
            }
            if (prelude == null) {
                // Generated bytecode can reference only named prelude formatters.
                throw new UnsupportedSchemaException(
                        "Cannot name the timestamp format at " + members.get(index).schema().id());
            }
            method.visitFieldInsn(GETSTATIC, PRELUDE, prelude.name(), "L" + PRELUDE + ";");
        }

        private static void checkCast(MethodVisitor method, Class<?> javaType, String expected) {
            if (!Type.getInternalName(javaType).equals(expected)) {
                method.visitTypeInsn(CHECKCAST, expected);
            }
        }

        private HttpBindingSchemaExtensions.Binding kindOf(int index) {
            return bindings[members.get(index).schema().memberIndex()];
        }

        private HttpBindingSchemaExtensions.MemberBinding memberBinding(int index) {
            return memberBindings[members.get(index).schema().memberIndex()];
        }

        private String headerName(int index) {
            HeaderName name = memberBinding(index).headerName();
            if (name == null) {
                throw new UnsupportedSchemaException(
                        "No resolved header name for " + members.get(index).schema().id());
            }
            return name.name();
        }

        private String wireName(int index) {
            String name = memberBinding(index).wireName();
            if (name == null) {
                throw new UnsupportedSchemaException(
                        "No resolved wire name for " + members.get(index).schema().id());
            }
            return name;
        }

        private boolean hasMediaType(int index) {
            return memberBinding(index).hasMediaType();
        }
    }
}
