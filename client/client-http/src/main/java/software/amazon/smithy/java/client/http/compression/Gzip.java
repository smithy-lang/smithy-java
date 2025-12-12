/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;

public final class Gzip implements CompressionAlgorithm {

    @Override
    public String algorithmId() {
        return "gzip";
    }

    @Override
    public DataStream compress(DataStream data) {
        if (!data.hasKnownLength()) { // Using streaming
            return DataStream.ofInputStream(
                    new GzipCompressingInputStream(data.asInputStream()),
                    data.contentType(),
                    -1);
        }

        try (var bos = new ByteBufferOutputStream();
                var in = data.asInputStream()) {
            var gzip = new GZIPOutputStream(bos);
            in.transferTo(gzip);
            gzip.close();
            return DataStream.ofBytes(bos.toByteBuffer().array());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
