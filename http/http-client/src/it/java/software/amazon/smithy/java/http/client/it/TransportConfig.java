/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer.H2ConnectionMode;

/**
 * Transport configurations for parameterized HTTP client tests.
 */
public enum TransportConfig {
    H1_CLEAR(HttpVersion.HTTP_1_1, false, null, HttpVersionPolicy.ENFORCE_HTTP_1_1),
    H1_TLS(HttpVersion.HTTP_1_1, true, null, HttpVersionPolicy.ENFORCE_HTTP_1_1),
    H2C(HttpVersion.HTTP_2, false, H2ConnectionMode.PRIOR_KNOWLEDGE, HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE),
    H2_TLS(HttpVersion.HTTP_2, true, H2ConnectionMode.PRIOR_KNOWLEDGE, HttpVersionPolicy.ENFORCE_HTTP_2),
    H2_ALPN(HttpVersion.HTTP_2, true, H2ConnectionMode.ALPN, HttpVersionPolicy.AUTOMATIC);

    private final HttpVersion httpVersion;
    private final boolean useTls;
    private final H2ConnectionMode h2Mode;
    private final HttpVersionPolicy versionPolicy;

    TransportConfig(HttpVersion httpVersion, boolean useTls, H2ConnectionMode h2Mode, HttpVersionPolicy versionPolicy) {
        this.httpVersion = httpVersion;
        this.useTls = useTls;
        this.h2Mode = h2Mode;
        this.versionPolicy = versionPolicy;
    }

    public HttpVersion httpVersion() {
        return httpVersion;
    }

    public boolean useTls() {
        return useTls;
    }

    public H2ConnectionMode h2Mode() {
        return h2Mode;
    }

    public HttpVersionPolicy versionPolicy() {
        return versionPolicy;
    }

    public boolean isHttp2() {
        return httpVersion == HttpVersion.HTTP_2;
    }
}
