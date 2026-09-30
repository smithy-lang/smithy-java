/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ACC_PUBLIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ALOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ASTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ATHROW;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.DLOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.DSTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.DUP;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.FLOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.FSTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ICONST_0;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ICONST_1;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IF_ACMPNE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ILOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKEINTERFACE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKESPECIAL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKESTATIC;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.INVOKEVIRTUAL;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.IRETURN;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.ISTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.LLOAD;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.LSTORE;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.NEW;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes.RETURN;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassWriter;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Label;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.MethodVisitor;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Type;
import software.amazon.smithy.utils.SmithyInternalApi;

/** Shared bytecode helpers for runtime codec backends. */
@SmithyInternalApi
public final class RuntimeCodegenBytecode {
    private RuntimeCodegenBytecode() {}

    public static void emitDefaultConstructor(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    // Generated readers call concrete setters, so only the exact builder class is safe.
    public static void emitAcceptsBuilder(ClassWriter writer, Class<?> builderClass) {
        MethodVisitor method = writer.visitMethod(
                ACC_PUBLIC,
                "acceptsBuilder",
                "(Lsoftware/amazon/smithy/java/core/schema/ShapeBuilder;)Z",
                null,
                null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Object",
                "getClass",
                "()Ljava/lang/Class;",
                false);
        method.visitLdcInsn(builderClass);
        Label wrongClass = new Label();
        method.visitJumpInsn(IF_ACMPNE, wrongClass);
        method.visitInsn(ICONST_1);
        method.visitInsn(IRETURN);
        method.visitLabel(wrongClass);
        method.visitInsn(ICONST_0);
        method.visitInsn(IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    public static void invoke(MethodVisitor method, Method target) {
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

    public static Method findBuild(Class<?> builderClass, Class<?> shapeClass) {
        for (Method method : builderClass.getMethods()) {
            if (method.getName().equals("build")
                    && method.getParameterCount() == 0
                    && method.getReturnType() == shapeClass) {
                return method;
            }
        }
        throw new UnsupportedSchemaException("No direct build method on " + builderClass.getName());
    }

    public static int fieldHash(String value) {
        int hash = 0;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash = 31 * hash + (b & 0xff);
        }
        return hash;
    }

    public static int storeOpcode(Class<?> type) {
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

    public static int loadOpcode(Class<?> type) {
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

    public static void emitThrow(MethodVisitor method, String message) {
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
