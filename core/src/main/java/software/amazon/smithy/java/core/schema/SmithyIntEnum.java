/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

/**
 * An intEnum shape.
 */
public interface SmithyIntEnum {

    /**
     * Get the value of the shape.
     *
     * @return the value of the shape.
     */
    int getValue();
}
