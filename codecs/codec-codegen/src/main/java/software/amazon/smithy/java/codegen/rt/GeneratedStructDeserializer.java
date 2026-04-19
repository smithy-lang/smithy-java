/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

/**
 * Interface implemented by runtime-generated specialized deserializers.
 */
public interface GeneratedStructDeserializer {
    SerializableStruct deserialize(Object ctx, ShapeBuilder<?> builder);
}
