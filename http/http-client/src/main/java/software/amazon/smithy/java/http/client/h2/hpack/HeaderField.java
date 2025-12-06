/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

/**
 * Decoded HPACK header field (name-value pair).
 *
 * <p>This is the public type returned by HPACK decoding operations.
 * Static table entries are pre-allocated and reused (zero allocation on lookup).
 *
 * @param name header field name (lowercase for HTTP/2)
 * @param value header field value
 */
public record HeaderField(String name, String value) {}
