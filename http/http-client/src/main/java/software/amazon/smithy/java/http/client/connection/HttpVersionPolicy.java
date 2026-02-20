/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

/**
 * HTTP protocol version policy for connection negotiation.
 */
public enum HttpVersionPolicy {
    /** HTTP/1.1 only. For TLS, negotiates only "http/1.1" via ALPN. */
    ENFORCE_HTTP_1_1(new String[] {"http/1.1"}),

    /** HTTP/2 over TLS only. Negotiates only "h2" via ALPN. Fails if server doesn't support. */
    ENFORCE_HTTP_2(new String[] {"h2"}),

    /** Prefer HTTP/2, fall back to HTTP/1.1. Uses HTTP/1.1 for cleartext. Recommended default. */
    AUTOMATIC(new String[] {"h2", "http/1.1"}),

    /** HTTP/2 over cleartext (h2c) using prior knowledge. */
    H2C_PRIOR_KNOWLEDGE(new String[] {"h2"});

    private final String[] alpnProtocols;

    HttpVersionPolicy(String[] alpnProtocols) {
        this.alpnProtocols = alpnProtocols;
    }

    /**
     * Get ALPN protocol strings for this policy.
     *
     * <p>Only applicable for TLS connections. For cleartext, use {@link #usesH2cForCleartext()}.
     *
     * @return array of ALPN protocol strings in preference order
     */
    public String[] alpnProtocols() {
        return alpnProtocols;
    }

    /**
     * Check if this policy uses h2c (HTTP/2 cleartext) for non-TLS connections.
     *
     * @return true if h2c prior knowledge should be used for cleartext
     */
    public boolean usesH2cForCleartext() {
        return this == H2C_PRIOR_KNOWLEDGE;
    }
}
