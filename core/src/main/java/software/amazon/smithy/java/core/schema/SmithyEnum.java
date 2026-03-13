/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

/**
 * An enum shape.
 */
public interface SmithyEnum {

    /**
     * Get the value of the shape.
     *
     * @return the value of the shape.
     */
    String getValue();
}
