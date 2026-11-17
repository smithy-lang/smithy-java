/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.java.core.serde.ToStringSerializer;

/**
 * Base class for known values of a generated {@code intEnum}.
 *
 * <p>Known values compare by identity.
 */
public abstract class IntEnumValue implements SmithyIntEnum, SerializableShape {

    private final int value;

    protected IntEnumValue(int value) {
        this.value = value;
    }

    @Override
    public final int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }
}
