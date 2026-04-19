/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;

/**
 * SPI for codec-specific direct bytecode generation using the ClassFile API.
 * Alternative to {@link CodecProfile} which generates Java source code for Janino compilation.
 */
public interface BytecodeCodecProfile {
    String name();

    GenerationResult generateSerializerBytecode(StructCodePlan plan, String className, String packageName);

    GenerationResult generateDeserializerBytecode(StructCodePlan plan, String className, String packageName);

    record GenerationResult(byte[] bytecode, Object classData) {}
}
