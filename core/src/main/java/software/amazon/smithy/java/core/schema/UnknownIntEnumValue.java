/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

/**
 * Base class of the unknown-value variant of a generated intEnum.
 *
 * <p>Unknown values compare by value, but only to unknown values of the same intEnum type.
 */
public abstract class UnknownIntEnumValue extends IntEnumValue {

    protected UnknownIntEnumValue(int value) {
        super(value);
    }

    /**
     * @return the unknown value.
     */
    public final int value() {
        return value;
    }

    @Override
    public final boolean equals(Object other) {
        return other != null && other.getClass() == getClass() && value == ((UnknownIntEnumValue) other).value;
    }

    @Override
    public final int hashCode() {
        return value;
    }
}
