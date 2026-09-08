/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

import java.util.List;

record ClassFileModel(
        int version,
        int access,
        String name,
        String superName,
        List<String> interfaces,
        List<FieldModel> fields,
        List<MethodModel> methods) {
    record FieldModel(int access, String name, String descriptor) {}

    record MethodModel(
            int access,
            String name,
            String descriptor,
            List<Instruction> instructions,
            List<TryCatch> tryCatches) {}

    record TryCatch(Label start, Label end, Label handler, String type) {}

    record Instruction(Kind kind, int opcode, Object[] operands) {
        enum Kind {
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
