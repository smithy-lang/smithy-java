/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;

/**
 * SPI for codec-specific source code generation.
 * Each codec (JSON, CBOR, etc.) implements this to generate specialized serializer/deserializer
 * Java source code that is compiled at runtime via Janino.
 */
public interface CodecProfile {
    String name();

    String generateSerializerSource(StructCodePlan plan, String className, String packageName);

    String generateDeserializerSource(StructCodePlan plan, String className, String packageName);
}
