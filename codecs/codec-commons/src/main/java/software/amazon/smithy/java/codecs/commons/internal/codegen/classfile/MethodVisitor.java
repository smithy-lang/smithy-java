/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassFileModel.Instruction;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassFileModel.Instruction.Kind;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassFileModel.TryCatch;

/**
 * Records generator instructions without linking the Java 21 classes to the
 * JDK ClassFile API.
 */
public final class MethodVisitor {
    private final List<Instruction> instructions = new ArrayList<>();
    private final List<TryCatch> tryCatches = new ArrayList<>();

    public void visitCode() {}

    public void visitInsn(int opcode) {
        add(Kind.INSN, opcode);
    }

    public void visitVarInsn(int opcode, int variable) {
        add(Kind.VAR, opcode, variable);
    }

    public void visitTypeInsn(int opcode, String type) {
        add(Kind.TYPE, opcode, type);
    }

    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        add(Kind.FIELD, opcode, owner, name, descriptor);
    }

    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        add(Kind.METHOD, opcode, owner, name, descriptor, isInterface);
    }

    public void visitJumpInsn(int opcode, Label target) {
        add(Kind.JUMP, opcode, target);
    }

    public void visitLabel(Label label) {
        add(Kind.LABEL, 0, label);
    }

    public void visitLdcInsn(Object value) {
        add(Kind.CONSTANT, 0, value);
    }

    public void visitIincInsn(int variable, int increment) {
        add(Kind.INCREMENT, 0, variable, increment);
    }

    public void visitLookupSwitchInsn(Label defaultTarget, int[] keys, Label[] targets) {
        add(Kind.LOOKUP_SWITCH, 0, defaultTarget, keys.clone(), targets.clone());
    }

    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        tryCatches.add(new TryCatch(start, end, handler, type));
    }

    public void visitMaxs(int maxStack, int maxLocals) {}

    public void visitEnd() {}

    List<Instruction> instructions() {
        return List.copyOf(instructions);
    }

    List<TryCatch> tryCatches() {
        return List.copyOf(tryCatches);
    }

    private void add(Kind kind, int opcode, Object... operands) {
        instructions.add(new Instruction(kind, opcode, operands));
    }
}
