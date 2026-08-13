/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonSettings;

interface GeneratedJsonCodec {
    void write(SerializableShape value, JsonCodegenWriter writer);

    SerializableShape read(byte[] source, ShapeBuilder<?> builder, JsonSettings settings);

    int scan(byte[] source, JsonSettings settings);
}
