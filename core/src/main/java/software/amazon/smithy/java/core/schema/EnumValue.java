/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.java.core.serde.ToStringSerializer;

/**
 * Base class for known values of a generated string enum.
 *
 * <p>Known values compare by identity.
 */
public abstract class EnumValue implements SmithyEnum, SerializableShape {

    private final String value;

    protected EnumValue(String value) {
        this.value = value;
    }

    @Override
    public final String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }
}
