/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

/**
 * HTTP protocol version negotiation policy for the Netty transport.
 */
public enum HttpVersionPolicy {
    /** HTTP/1.1 only. For TLS, advertises only "http/1.1" via ALPN. */
    ENFORCE_HTTP_1_1(new String[] {"http/1.1"}),

    /** HTTP/2 over TLS only. Advertises only "h2" via ALPN. Fails if server doesn't support. */
    ENFORCE_HTTP_2(new String[] {"h2"}),

    /** Prefer HTTP/2, fall back to HTTP/1.1. Uses HTTP/1.1 for cleartext. */
    AUTOMATIC(new String[] {"h2", "http/1.1"}),

    /** HTTP/2 over cleartext (h2c) using prior knowledge. No ALPN. */
    H2C_PRIOR_KNOWLEDGE(new String[] {"h2"});

    private final String[] alpnProtocols;

    HttpVersionPolicy(String[] alpnProtocols) {
        this.alpnProtocols = alpnProtocols;
    }

    public String[] alpnProtocols() {
        return alpnProtocols.clone();
    }

    public boolean usesH2cForCleartext() {
        return this == H2C_PRIOR_KNOWLEDGE;
    }
}
