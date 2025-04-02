/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

/**
 * Whether documentation is applied to an element, and if so, how.
 */
public enum ApplyDocumentation {
    /**
     * Generate documentation for the class.
     */
    DOCUMENT,

    /**
     * Do not write documentation (e.g., implicitly inherit documentation of a parent).
     */
    NONE
}
