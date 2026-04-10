/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * High-performance native JSON serialization/deserialization for smithy-java.
 *
 * <p>Writes and parses JSON bytes directly without Jackson on the hot path,
 * exploiting Smithy schema knowledge for pre-computed field names and hash-based
 * field matching.
 */
package software.amazon.smithy.java.json.smithy;
