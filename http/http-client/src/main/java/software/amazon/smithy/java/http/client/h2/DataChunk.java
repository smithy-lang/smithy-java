/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.nio.ByteBuffer;

/**
 * Data chunk from an HTTP/2 DATA frame.
 *
 * <p>The buffer is in read mode (position=0, limit=length of data).
 * Ownership is transferred to the consumer, who must return it to the pool
 * when done.
 *
 * @param data buffer containing frame data (ready for reading)
 * @param endStream true if this is the final chunk (END_STREAM flag was set)
 * @param flowControlBytes DATA frame payload bytes charged to HTTP/2 receive windows
 */
record DataChunk(ByteBuffer data, boolean endStream, int flowControlBytes) {}
