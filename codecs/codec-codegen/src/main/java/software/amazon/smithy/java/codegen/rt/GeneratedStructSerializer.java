/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Interface implemented by runtime-generated specialized serializers.
 */
public interface GeneratedStructSerializer {
    void serialize(SerializableStruct obj, WriterContext ctx);
}
