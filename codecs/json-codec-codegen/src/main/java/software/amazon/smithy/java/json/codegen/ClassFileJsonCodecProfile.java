/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import software.amazon.smithy.java.codegen.rt.BytecodeCodecProfile;
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;

/**
 * ClassFile API-based bytecode codec profile for JSON serialization.
 * Alternative to {@link JsonCodecProfile} which generates Java source for Janino compilation.
 */
public final class ClassFileJsonCodecProfile implements BytecodeCodecProfile {

    @Override
    public String name() {
        return "json";
    }

    @Override
    public GenerationResult generateSerializerBytecode(
            StructCodePlan plan,
            String className,
            String packageName
    ) {
        return new ClassFileJsonSerializerGenerator().generate(plan, className, packageName);
    }

    @Override
    public GenerationResult generateDeserializerBytecode(
            StructCodePlan plan,
            String className,
            String packageName
    ) {
        return new ClassFileJsonDeserializerGenerator().generate(plan, className, packageName);
    }
}
