/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import java.util.Map;
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;

/**
 * SPI for codec-specific direct bytecode generation using the ClassFile API.
 */
public interface CodecProfile {
    String name();

    GenerationResult generateSerializerBytecode(
            StructCodePlan plan,
            String className,
            String packageName,
            Map<String, GeneratedStructSerializer> resolvedSerializers
    );

    GenerationResult generateDeserializerBytecode(
            StructCodePlan plan,
            String className,
            String packageName,
            Map<String, GeneratedStructDeserializer> resolvedDeserializers
    );

    record GenerationResult(byte[] bytecode, Object classData) {}
}
