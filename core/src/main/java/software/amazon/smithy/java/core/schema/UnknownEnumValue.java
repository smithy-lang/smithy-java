/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.Objects;

/**
 * Base class of the unknown-value variant of a generated string enum.
 *
 * <p>Unknown values compare by value, but only to unknown values of the same enum type.
 */
public abstract class UnknownEnumValue extends EnumValue {

    protected UnknownEnumValue(String value) {
        super(Objects.requireNonNull(value, "Value cannot be null"));
    }

    /**
     * Returns the unknown value. This mirrors the accessor of the record this class replaced.
     *
     * @return the unknown value.
     */
    public final String value() {
        return getValue();
    }

    @Override
    public final boolean equals(Object other) {
        return other != null && other.getClass() == getClass()
                && getValue().equals(((UnknownEnumValue) other).getValue());
    }

    @Override
    public final int hashCode() {
        return getValue().hashCode();
    }
}
