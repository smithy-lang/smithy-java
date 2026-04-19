/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import software.amazon.smithy.java.codegen.rt.CodecProfile;
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;

/**
 * JSON codec profile that generates specialized serializer and deserializer source code.
 */
public final class JsonCodecProfile implements CodecProfile {

    @Override
    public String name() {
        return "json";
    }

    @Override
    public String generateSerializerSource(StructCodePlan plan, String className, String packageName) {
        return new JsonSerializerCodegen().generate(plan, className, packageName);
    }

    @Override
    public String generateDeserializerSource(StructCodePlan plan, String className, String packageName) {
        return new JsonDeserializerCodegen().generate(plan, className, packageName);
    }
}
