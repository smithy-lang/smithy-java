/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Specifies which code generation mode(s) to use.
 */
@SmithyUnstableApi
public enum CodegenMode {
    CLIENT,
    SERVER,
    TYPES
}
