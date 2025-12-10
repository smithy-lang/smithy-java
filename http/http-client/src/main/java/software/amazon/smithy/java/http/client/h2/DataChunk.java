/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

/**
 * Data chunk containing a buffer and metadata.
 */
record DataChunk(byte[] data, int length, boolean endStream) {}
