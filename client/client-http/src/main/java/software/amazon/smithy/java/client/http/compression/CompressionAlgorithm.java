/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Represents a compression algorithm that can be used to compress request
 * bodies.
 */
public interface CompressionAlgorithm {
    /**
     * The ID of the checksum algorithm. This is matched against the algorithm
     * names used in the trait e.g. "gzip"
     */
    String algorithmId();

    /**
     * Compresses content of fixed length
     */
    DataStream compress(DataStream data);
}
