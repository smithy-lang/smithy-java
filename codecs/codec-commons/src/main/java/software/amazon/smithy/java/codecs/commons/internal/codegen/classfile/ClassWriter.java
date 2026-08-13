/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassFileModel.FieldModel;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassFileModel.MethodModel;

/**
 * Java 21-compatible recording facade for the optional JDK ClassFile encoder.
 */
public final class ClassWriter {
    public static final int COMPUTE_FRAMES = 1;
    public static final int COMPUTE_MAXS = 2;

    private static final String EMITTER =
            "software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.JdkClassFileEmitter";

    private final List<FieldModel> fields = new ArrayList<>();
    private final List<MethodEntry> methods = new ArrayList<>();
    private int version;
    private int access;
    private String name;
    private String superName;
    private List<String> interfaces;

    public ClassWriter(int flags) {}

    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces
    ) {
        this.version = version;
        this.access = access;
        this.name = name;
        this.superName = superName;
        this.interfaces = List.of(interfaces.clone());
    }

    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        fields.add(new FieldModel(access, name, descriptor));
        return new FieldVisitor();
    }

    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
    ) {
        MethodVisitor visitor = new MethodVisitor();
        methods.add(new MethodEntry(access, name, descriptor, visitor));
        return visitor;
    }

    public void visitEnd() {}

    public byte[] toByteArray() {
        if (Runtime.version().feature() < 24) {
            throw new UnsupportedOperationException("Runtime codec generation requires JDK 24 or newer");
        }
        List<MethodModel> methodModels = methods.stream()
                .map(entry -> new MethodModel(
                        entry.access,
                        entry.name,
                        entry.descriptor,
                        entry.visitor.instructions(),
                        entry.visitor.tryCatches()))
                .toList();
        ClassFileModel model =
                new ClassFileModel(version, access, name, superName, interfaces, List.copyOf(fields), methodModels);
        try {
            return (byte[]) EmitterHolder.EMIT.invokeExact(model);
        } catch (Throwable cause) {
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("JDK ClassFile emission failed", cause);
        }
    }

    private static final class EmitterHolder {
        private static final MethodHandle EMIT = load();

        private static MethodHandle load() {
            try {
                Class<?> emitter = Class.forName(EMITTER, true, ClassWriter.class.getClassLoader());
                return MethodHandles.lookup()
                        .findStatic(
                                emitter,
                                "emit",
                                MethodType.methodType(byte[].class, ClassFileModel.class))
                        .asType(MethodType.methodType(byte[].class, ClassFileModel.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("JDK ClassFile emitter is unavailable", e);
            }
        }
    }

    private record MethodEntry(int access, String name, String descriptor, MethodVisitor visitor) {}
}
