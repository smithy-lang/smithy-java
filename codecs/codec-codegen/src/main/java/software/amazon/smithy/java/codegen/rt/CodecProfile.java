/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;

/**
 * SPI for codec-specific direct bytecode generation using the ClassFile API.
 */
public interface CodecProfile {
    String name();

    GenerationResult generateSerializerBytecode(StructCodePlan plan, String className, String packageName);

    GenerationResult generateDeserializerBytecode(StructCodePlan plan, String className, String packageName);

    record GenerationResult(byte[] bytecode, Object classData) {}
}
