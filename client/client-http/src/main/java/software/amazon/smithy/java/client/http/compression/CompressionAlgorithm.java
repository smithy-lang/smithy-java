/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import java.util.List;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.utils.ListUtils;

/**
 * Represents a compression algorithm that can be used to compress request
 * bodies.
 */
public interface CompressionAlgorithm {
    /**
     * The ID of the compression algorithm. This is matched against the algorithm
     * names used in the trait e.g. "gzip"
     */
    String algorithmId();

    /**
     * Compresses content of fixed length
     */
    DataStream compress(DataStream data);

    List<CompressionAlgorithm> SUPPORTED_ALGORITHMS = List.of(new Gzip());

    static List<CompressionAlgorithm> supportedAlgorithms() {
        return SUPPORTED_ALGORITHMS;
    }
}
