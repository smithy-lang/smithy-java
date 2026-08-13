/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

/**
 * Minimal JVM constants used by runtime codec generators.
 */
public interface Opcodes {
    int V17 = 61;

    int ACC_PUBLIC = 0x0001;
    int ACC_PRIVATE = 0x0002;
    int ACC_STATIC = 0x0008;
    int ACC_FINAL = 0x0010;
    int ACC_SUPER = 0x0020;

    int ACONST_NULL = 1;
    int ICONST_0 = 3;
    int ICONST_1 = 4;
    int ILOAD = 21;
    int LLOAD = 22;
    int FLOAD = 23;
    int DLOAD = 24;
    int ALOAD = 25;
    int ISTORE = 54;
    int LSTORE = 55;
    int FSTORE = 56;
    int DSTORE = 57;
    int ASTORE = 58;
    int POP = 87;
    int DUP = 89;
    int IRETURN = 172;
    int ARETURN = 176;
    int RETURN = 177;
    int GETSTATIC = 178;
    int PUTSTATIC = 179;
    int INVOKEVIRTUAL = 182;
    int INVOKESPECIAL = 183;
    int INVOKESTATIC = 184;
    int INVOKEINTERFACE = 185;
    int NEW = 187;
    int ARRAYLENGTH = 190;
    int ATHROW = 191;
    int CHECKCAST = 192;
    int INSTANCEOF = 193;
    int IFEQ = 153;
    int IFNE = 154;
    int IF_ICMPGE = 162;
    int GOTO = 167;
    int IFNULL = 198;
    int IFNONNULL = 199;
}
