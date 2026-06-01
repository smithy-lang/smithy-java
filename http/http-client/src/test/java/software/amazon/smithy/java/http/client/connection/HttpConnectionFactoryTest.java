/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class HttpConnectionFactoryTest {

    @Test
    void acceptsNegotiatedH2WhenPolicyAllowsIt() throws IOException {
        assertEquals(HttpConnectionFactory.Protocol.H2,
                HttpConnectionFactory.selectProtocol("h2", true, HttpVersionPolicy.AUTOMATIC));
        assertEquals(HttpConnectionFactory.Protocol.H2,
                HttpConnectionFactory.selectProtocol("h2", true, HttpVersionPolicy.ENFORCE_HTTP_2));
    }

    @Test
    void rejectsNegotiatedH2WhenH1IsEnforced() {
        assertThrows(
                IOException.class,
                () -> HttpConnectionFactory.selectProtocol("h2", true, HttpVersionPolicy.ENFORCE_HTTP_1_1));
    }

    @Test
    void acceptsNegotiatedH1WhenPolicyAllowsIt() throws IOException {
        assertEquals(HttpConnectionFactory.Protocol.H1,
                HttpConnectionFactory.selectProtocol("http/1.1", true, HttpVersionPolicy.AUTOMATIC));
        assertEquals(HttpConnectionFactory.Protocol.H1,
                HttpConnectionFactory.selectProtocol("http/1.1", true, HttpVersionPolicy.ENFORCE_HTTP_1_1));
    }

    @Test
    void rejectsNegotiatedH1WhenH2IsEnforced() {
        assertThrows(
                IOException.class,
                () -> HttpConnectionFactory.selectProtocol("http/1.1", true, HttpVersionPolicy.ENFORCE_HTTP_2));
    }

    @Test
    void rejectsUnsupportedNegotiatedProtocol() {
        assertThrows(
                IOException.class,
                () -> HttpConnectionFactory.selectProtocol("spdy/3", true, HttpVersionPolicy.AUTOMATIC));
    }

    @Test
    void fallsBackToH1ForSecureAutomaticWhenNoAlpnWasNegotiated() throws IOException {
        assertEquals(HttpConnectionFactory.Protocol.H1,
                HttpConnectionFactory.selectProtocol(null, true, HttpVersionPolicy.AUTOMATIC));
        assertEquals(HttpConnectionFactory.Protocol.H1,
                HttpConnectionFactory.selectProtocol("", true, HttpVersionPolicy.AUTOMATIC));
    }

    @Test
    void rejectsSecureEnforcedH2WhenNoAlpnWasNegotiated() {
        assertThrows(
                IOException.class,
                () -> HttpConnectionFactory.selectProtocol(null, true, HttpVersionPolicy.ENFORCE_HTTP_2));
    }

    @Test
    void selectsH2cForCleartextPriorKnowledge() throws IOException {
        assertEquals(HttpConnectionFactory.Protocol.H2,
                HttpConnectionFactory.selectProtocol(null, false, HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE));
    }

    @Test
    void rejectsCleartextEnforcedH2WithoutH2cPriorKnowledge() {
        assertThrows(
                IOException.class,
                () -> HttpConnectionFactory.selectProtocol(null, false, HttpVersionPolicy.ENFORCE_HTTP_2));
    }
}
