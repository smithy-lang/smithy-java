/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.smithy;

import java.time.Duration;
import software.amazon.smithy.java.client.http.HttpTransportConfig;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

/**
 * Transport configuration for the Smithy HTTP client, extending common settings
 * with connection pool and HTTP/2 tuning options.
 */
public final class SmithyHttpTransportConfig extends HttpTransportConfig {

    private Integer maxConnections;
    private Duration maxIdleTime;
    private Integer h2StreamsPerConnection;
    private Integer h2InitialWindowSize;
    private HttpVersionPolicy httpVersionPolicy;

    public Integer maxConnections() {
        return maxConnections;
    }

    public SmithyHttpTransportConfig maxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public Duration maxIdleTime() {
        return maxIdleTime;
    }

    public SmithyHttpTransportConfig maxIdleTime(Duration maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
        return this;
    }

    public Integer h2StreamsPerConnection() {
        return h2StreamsPerConnection;
    }

    public SmithyHttpTransportConfig h2StreamsPerConnection(int h2StreamsPerConnection) {
        this.h2StreamsPerConnection = h2StreamsPerConnection;
        return this;
    }

    public Integer h2InitialWindowSize() {
        return h2InitialWindowSize;
    }

    public SmithyHttpTransportConfig h2InitialWindowSize(int h2InitialWindowSize) {
        this.h2InitialWindowSize = h2InitialWindowSize;
        return this;
    }

    public HttpVersionPolicy httpVersionPolicy() {
        return httpVersionPolicy;
    }

    public SmithyHttpTransportConfig httpVersionPolicy(HttpVersionPolicy httpVersionPolicy) {
        this.httpVersionPolicy = httpVersionPolicy;
        return this;
    }

    @Override
    public SmithyHttpTransportConfig fromDocument(Document doc) {
        super.fromDocument(doc);
        var config = httpConfig(doc);
        if (config == null) {
            return this;
        }

        var maxConns = config.get("maxConnections");
        if (maxConns != null) {
            this.maxConnections = maxConns.asInteger();
        }

        var idle = config.get("maxIdleTimeMs");
        if (idle != null) {
            this.maxIdleTime = Duration.ofMillis(idle.asLong());
        }

        var h2Streams = config.get("h2StreamsPerConnection");
        if (h2Streams != null) {
            this.h2StreamsPerConnection = h2Streams.asInteger();
        }

        var h2Window = config.get("h2InitialWindowSize");
        if (h2Window != null) {
            this.h2InitialWindowSize = h2Window.asInteger();
        }

        var versionPolicy = config.get("httpVersionPolicy");
        if (versionPolicy != null) {
            this.httpVersionPolicy = HttpVersionPolicy.valueOf(versionPolicy.asString());
        }

        return this;
    }
}
