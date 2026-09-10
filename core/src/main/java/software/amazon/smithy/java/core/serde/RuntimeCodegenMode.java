/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

/** Controls runtime code generation and fallback behavior. */
public enum RuntimeCodegenMode {
    /** Always use interpreted serde. */
    DISABLED,

    /** Generate when possible and fall back when unavailable. */
    ENABLED,

    /** Generate or fail instead of falling back. */
    STRICT
}
