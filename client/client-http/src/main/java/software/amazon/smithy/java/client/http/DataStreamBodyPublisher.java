/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Adapts a Smithy {@link DataStream} to the JDK HttpClient's {@link HttpRequest.BodyPublisher} contract.
 */
final class DataStreamBodyPublisher implements HttpRequest.BodyPublisher {
    private final DataStream dataStream;

    DataStreamBodyPublisher(DataStream dataStream) {
        this.dataStream = dataStream;
    }

    @Override
    public long contentLength() {
        return dataStream.contentLength();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        dataStream.subscribe(subscriber);
    }
}
