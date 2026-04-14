/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

/**
 * Data chunk from an HTTP/2 DATA frame.
 *
 * @param data buffer containing frame data (ownership transferred to consumer)
 * @param length actual data length (can be less than {@code data.length})
 * @param endStream true if this is the final chunk (END_STREAM flag was set)
 */
record DataChunk(byte[] data, int length, boolean endStream) {}
