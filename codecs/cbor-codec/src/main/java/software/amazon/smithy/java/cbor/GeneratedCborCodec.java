/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

interface GeneratedCborCodec {
    void write(SerializableShape value, CborSerializer writer);

    SerializableShape read(byte[] source, ShapeBuilder<?> builder, CborSettings settings);
}
