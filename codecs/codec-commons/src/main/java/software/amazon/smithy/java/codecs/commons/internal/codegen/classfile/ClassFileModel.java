/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

import java.util.List;

/**
 * Generation-time class model lowered by the optional JDK ClassFile backend.
 */
public record ClassFileModel(
        int version,
        int access,
        String name,
        String superName,
        List<String> interfaces,
        List<FieldModel> fields,
        List<MethodModel> methods) {
    public record FieldModel(int access, String name, String descriptor) {}

    public record MethodModel(
            int access,
            String name,
            String descriptor,
            List<Instruction> instructions,
            List<TryCatch> tryCatches) {}

    public record TryCatch(Label start, Label end, Label handler, String type) {}

    public record Instruction(Kind kind, int opcode, Object[] operands) {
        public enum Kind {
            INSN,
            VAR,
            TYPE,
            FIELD,
            METHOD,
            JUMP,
            LABEL,
            CONSTANT,
            INCREMENT,
            LOOKUP_SWITCH
        }
    }
}
