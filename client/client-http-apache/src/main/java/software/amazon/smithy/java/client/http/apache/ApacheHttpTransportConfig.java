/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.time.Duration;
import software.amazon.smithy.java.client.http.HttpTransportConfig;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Configuration for {@link ApacheHttpClientTransport}.
 */
public final class ApacheHttpTransportConfig extends HttpTransportConfig {
    private int maxConnectionsPerHost = 20;
    private int ioThreads = 1;
    private int h2StreamsPerConnection = 100;
    private Duration acquireTimeout = Duration.ofSeconds(30);
    private int readBufferSize = 64 * 1024;

    public int maxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public ApacheHttpTransportConfig maxConnectionsPerHost(int value) {
        this.maxConnectionsPerHost = value;
        return this;
    }

    public int ioThreads() {
        return ioThreads;
    }

    public ApacheHttpTransportConfig ioThreads(int value) {
        this.ioThreads = value;
        return this;
    }

    public int h2StreamsPerConnection() {
        return h2StreamsPerConnection;
    }

    public ApacheHttpTransportConfig h2StreamsPerConnection(int value) {
        this.h2StreamsPerConnection = value;
        return this;
    }

    public Duration acquireTimeout() {
        return acquireTimeout;
    }

    public ApacheHttpTransportConfig acquireTimeout(Duration value) {
        this.acquireTimeout = value;
        return this;
    }

    public int readBufferSize() {
        return readBufferSize;
    }

    public ApacheHttpTransportConfig readBufferSize(int value) {
        this.readBufferSize = value;
        return this;
    }

    @Override
    public ApacheHttpTransportConfig fromDocument(Document doc) {
        super.fromDocument(doc);
        var config = doc.asStringMap();

        var maxConns = config.get("maxConnectionsPerHost");
        if (maxConns != null) {
            this.maxConnectionsPerHost = maxConns.asInteger();
        }

        var threads = config.get("ioThreads");
        if (threads != null) {
            this.ioThreads = threads.asInteger();
        }

        var streams = config.get("h2StreamsPerConnection");
        if (streams != null) {
            this.h2StreamsPerConnection = streams.asInteger();
        }

        var acquire = config.get("acquireTimeoutMs");
        if (acquire != null) {
            this.acquireTimeout = Duration.ofMillis(acquire.asLong());
        }

        var readBuffer = config.get("readBufferSize");
        if (readBuffer != null) {
            this.readBufferSize = readBuffer.asInteger();
        }

        return this;
    }
}
