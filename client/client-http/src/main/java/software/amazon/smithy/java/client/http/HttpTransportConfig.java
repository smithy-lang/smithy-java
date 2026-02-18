/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.time.Duration;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * Common HTTP transport configuration shared across transport implementations.
 *
 * <p>Subclass this to add transport-specific settings.
 */
public class HttpTransportConfig {

    private Duration requestTimeout;
    private Duration connectTimeout;
    private HttpVersion httpVersion;

    /**
     * Per-request timeout. Null means no timeout (use client default).
     *
     * <p>Defined in the document data as milliseconds.
     *
     * @return request timeout.
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    public HttpTransportConfig requestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    /**
     * TCP connect timeout. Null means use transport default.
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    public HttpTransportConfig connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * HTTP version preference. Null means use transport default.
     *
     * <p>Defined in the document data as "http/1.0", "http/1.1", or "h2".
     */
    public HttpVersion httpVersion() {
        return httpVersion;
    }

    public HttpTransportConfig httpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    /**
     * Populate common fields from a Document config.
     *
     * <p>Expects settings under an {@code "http"} key:
     * <pre>{@code
     * {
     *   "http": {
     *     "requestTimeoutMs": 5000,
     *     "connectTimeoutMs": 3000,
     *     "httpVersion": "HTTP/2.0"
     *   }
     * }
     * }</pre>
     *
     * <p>Subclasses should call {@code super.fromDocument(doc)} then parse their own fields
     * from {@link #httpConfig(Document)}.
     *
     * @param doc configuration document
     * @return this config for chaining
     */
    public HttpTransportConfig fromDocument(Document doc) {
        var config = httpConfig(doc);
        if (config == null) {
            return this;
        }

        var timeout = config.get("requestTimeoutMs");
        if (timeout != null) {
            this.requestTimeout = Duration.ofMillis(timeout.asLong());
        }

        var connTimeout = config.get("connectTimeoutMs");
        if (connTimeout != null) {
            this.connectTimeout = Duration.ofMillis(connTimeout.asLong());
        }

        var version = config.get("httpVersion");
        if (version != null) {
            this.httpVersion = HttpVersion.from(version.asString());
        }

        return this;
    }

    /**
     * Extract the {@code "http"} config map from a Document, or null if absent.
     *
     * @param doc the root configuration document
     * @return the http config map, or null
     */
    protected static Map<String, Document> httpConfig(Document doc) {
        var http = doc.asStringMap().get("http");
        return http != null ? http.asStringMap() : null;
    }
}
