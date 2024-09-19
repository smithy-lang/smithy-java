/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

final class InputStreamDataStream implements DataStream {

    private final Supplier<InputStream> supplier;
    private final String contentType;
    private final long contentLength;
    private Flow.Publisher<ByteBuffer> publisher;

    InputStreamDataStream(Supplier<InputStream> supplier, String contentType, long contentLength) {
        this.supplier = supplier;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public CompletableFuture<InputStream> asInputStream() {
        return CompletableFuture.completedFuture(supplier.get());
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        var p = publisher;
        if (p == null) {
            p = publisher = HttpRequest.BodyPublishers.ofInputStream(supplier);
        }
        p.subscribe(subscriber);
    }
}
