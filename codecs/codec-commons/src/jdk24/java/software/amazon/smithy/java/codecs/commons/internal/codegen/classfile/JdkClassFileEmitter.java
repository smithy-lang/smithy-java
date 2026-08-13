/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassFileModel.Instruction;

/**
 * JDK 24+ lowering of the Java 21 generation-time instruction model.
 */
public final class JdkClassFileEmitter {
    private JdkClassFileEmitter() {}

    public static byte[] emit(ClassFileModel model) {
        ClassDesc thisClass = classDesc(model.name());
        return ClassFile.of().build(thisClass, builder -> {
            builder.withVersion(model.version(), 0);
            builder.withFlags(model.access());
            builder.withSuperclass(classDesc(model.superName()));
            builder.withInterfaceSymbols(model.interfaces().stream().map(JdkClassFileEmitter::classDesc).toList());
            for (ClassFileModel.FieldModel field : model.fields()) {
                builder.withField(field.name(), ClassDesc.ofDescriptor(field.descriptor()), field.access());
            }
            for (ClassFileModel.MethodModel method : model.methods()) {
                builder.withMethodBody(
                        method.name(),
                        MethodTypeDesc.ofDescriptor(method.descriptor()),
                        method.access(),
                        code -> emitCode(code, method));
            }
        });
    }

    private static void emitCode(CodeBuilder code, ClassFileModel.MethodModel method) {
        Map<Label, java.lang.classfile.Label> labels = new IdentityHashMap<>();
        for (Instruction instruction : method.instructions()) {
            Object[] operands = instruction.operands();
            switch (instruction.kind()) {
                case INSN -> emitInsn(code, instruction.opcode());
                case VAR -> emitVar(code, instruction.opcode(), (int) operands[0]);
                case TYPE -> emitType(code, instruction.opcode(), (String) operands[0]);
                case FIELD -> code.fieldAccess(
                        opcode(instruction.opcode()),
                        classDesc((String) operands[0]),
                        (String) operands[1],
                        ClassDesc.ofDescriptor((String) operands[2]));
                case METHOD -> code.invoke(
                        opcode(instruction.opcode()),
                        classDesc((String) operands[0]),
                        (String) operands[1],
                        MethodTypeDesc.ofDescriptor((String) operands[2]),
                        (boolean) operands[3]);
                case JUMP -> code.branch(
                        opcode(instruction.opcode()),
                        label(code, labels, (Label) operands[0]));
                case LABEL -> code.labelBinding(label(code, labels, (Label) operands[0]));
                case CONSTANT -> emitConstant(code, operands[0]);
                case INCREMENT -> code.iinc((int) operands[0], (int) operands[1]);
                case LOOKUP_SWITCH -> emitLookupSwitch(code, labels, operands);
            }
        }
        for (ClassFileModel.TryCatch tryCatch : method.tryCatches()) {
            code.exceptionCatch(
                    label(code, labels, tryCatch.start()),
                    label(code, labels, tryCatch.end()),
                    label(code, labels, tryCatch.handler()),
                    classDesc(tryCatch.type()));
        }
    }

    private static void emitInsn(CodeBuilder code, int opcode) {
        switch (opcode) {
            case Opcodes.ACONST_NULL -> code.aconst_null();
            case Opcodes.ICONST_0 -> code.iconst_0();
            case Opcodes.ICONST_1 -> code.iconst_1();
            case Opcodes.POP -> code.pop();
            case Opcodes.DUP -> code.dup();
            case Opcodes.IRETURN -> code.ireturn();
            case Opcodes.ARETURN -> code.areturn();
            case Opcodes.RETURN -> code.return_();
            case Opcodes.ARRAYLENGTH -> code.arraylength();
            case Opcodes.ATHROW -> code.athrow();
            default -> throw unsupported("instruction", opcode);
        }
    }

    private static void emitVar(CodeBuilder code, int opcode, int variable) {
        switch (opcode) {
            case Opcodes.ILOAD -> code.iload(variable);
            case Opcodes.LLOAD -> code.lload(variable);
            case Opcodes.FLOAD -> code.fload(variable);
            case Opcodes.DLOAD -> code.dload(variable);
            case Opcodes.ALOAD -> code.aload(variable);
            case Opcodes.ISTORE -> code.istore(variable);
            case Opcodes.LSTORE -> code.lstore(variable);
            case Opcodes.FSTORE -> code.fstore(variable);
            case Opcodes.DSTORE -> code.dstore(variable);
            case Opcodes.ASTORE -> code.astore(variable);
            default -> throw unsupported("variable instruction", opcode);
        }
    }

    private static void emitType(CodeBuilder code, int opcode, String type) {
        switch (opcode) {
            case Opcodes.NEW -> code.new_(classDesc(type));
            case Opcodes.CHECKCAST -> code.checkcast(classDesc(type));
            case Opcodes.INSTANCEOF -> code.instanceOf(classDesc(type));
            default -> throw unsupported("type instruction", opcode);
        }
    }

    private static void emitConstant(CodeBuilder code, Object value) {
        if (value instanceof String string) {
            code.ldc(code.constantPool().stringEntry(string));
        } else if (value instanceof Integer integer) {
            code.loadConstant(integer);
        } else if (value instanceof Long longValue) {
            code.loadConstant(longValue);
        } else if (value instanceof Float floatValue) {
            code.loadConstant(floatValue);
        } else if (value instanceof Double doubleValue) {
            code.loadConstant(doubleValue);
        } else {
            throw new IllegalArgumentException("Unsupported generated constant: " + value);
        }
    }

    private static void emitLookupSwitch(
            CodeBuilder code,
            Map<Label, java.lang.classfile.Label> labels,
            Object[] operands
    ) {
        Label defaultTarget = (Label) operands[0];
        int[] keys = (int[]) operands[1];
        Label[] targets = (Label[]) operands[2];
        List<SwitchCase> cases = new ArrayList<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            cases.add(SwitchCase.of(keys[i], label(code, labels, targets[i])));
        }
        code.lookupswitch(label(code, labels, defaultTarget), cases);
    }

    private static java.lang.classfile.Label label(
            CodeBuilder code,
            Map<Label, java.lang.classfile.Label> labels,
            Label label
    ) {
        return labels.computeIfAbsent(label, ignored -> code.newLabel());
    }

    private static Opcode opcode(int value) {
        for (Opcode opcode : Opcode.values()) {
            if (opcode.bytecode() == value) {
                return opcode;
            }
        }
        throw unsupported("opcode", value);
    }

    private static ClassDesc classDesc(String internalName) {
        return ClassDesc.ofInternalName(internalName);
    }

    private static IllegalArgumentException unsupported(String kind, int opcode) {
        return new IllegalArgumentException("Unsupported generated " + kind + ": " + opcode);
    }
}
