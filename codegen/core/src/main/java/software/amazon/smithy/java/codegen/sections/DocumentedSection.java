/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

/**
 * Applied to sections that can have documentation.
 */
public interface DocumentedSection {
    default ApplyDocumentation applyDocumentation() {
        return ApplyDocumentation.DOCUMENT;
    }
}
