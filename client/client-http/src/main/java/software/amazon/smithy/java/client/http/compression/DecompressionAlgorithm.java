/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import java.util.List;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Represents a compression algorithm that can be used to decompress response
 * bodies.
 */
public interface DecompressionAlgorithm {
    /**
     * The ID of the compression algorithm. This is matched against the
     * {@code Content-Encoding} of a response e.g. "gzip"
     */
    String algorithmId();

    /**
     * Decompresses content. The decompressed length is unknown until the data has been read.
     */
    DataStream decompress(DataStream data);

    List<DecompressionAlgorithm> SUPPORTED_ALGORITHMS = List.of(new Gzip());

    static List<DecompressionAlgorithm> supportedAlgorithms() {
        return SUPPORTED_ALGORITHMS;
    }
}
