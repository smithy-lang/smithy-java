/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.smithy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

class SmithyHttpClientTransportTest {

    @Test
    void defaultConstructorCreatesTransport() {
        var transport = new SmithyHttpClientTransport();

        assertEquals(HttpMessageExchange.INSTANCE, transport.messageExchange());
    }

    @Test
    void factorySettings() {
        var factory = new SmithyHttpClientTransport.Factory();

        assertEquals("http-smithy", factory.name());
        assertEquals(HttpMessageExchange.INSTANCE, factory.messageExchange());
    }

    @Test
    void configParsesAllFields() {
        var config = new SmithyHttpTransportConfig().fromDocument(Document.of(Map.of(
                "http",
                Document.of(Map.of(
                        "requestTimeoutMs",
                        Document.of(5000),
                        "connectTimeoutMs",
                        Document.of(3000),
                        "maxConnections",
                        Document.of(50),
                        "maxIdleTimeMs",
                        Document.of(60000),
                        "h2StreamsPerConnection",
                        Document.of(200),
                        "h2InitialWindowSize",
                        Document.of(1048576),
                        "httpVersionPolicy",
                        Document.of("H2C_PRIOR_KNOWLEDGE"))))));

        assertEquals(5000, config.requestTimeout().toMillis());
        assertEquals(3000, config.connectTimeout().toMillis());
        assertEquals(50, config.maxConnections());
        assertEquals(60000, config.maxIdleTime().toMillis());
        assertEquals(200, config.h2StreamsPerConnection());
        assertEquals(1048576, config.h2InitialWindowSize());
        assertEquals(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE, config.httpVersionPolicy());
    }

    @Test
    void configHandlesMissingHttpKey() {
        var config = new SmithyHttpTransportConfig().fromDocument(Document.of(Map.of()));

        assertNull(config.requestTimeout());
        assertNull(config.maxConnections());
    }

    @Test
    void factoryCreatesTransportWithVersionPolicy() {
        var factory = new SmithyHttpClientTransport.Factory();

        assertDoesNotThrow(() -> {
            factory.createTransport(Document.of(Map.of(
                    "http",
                    Document.of(Map.of("httpVersionPolicy", Document.of("H2C_PRIOR_KNOWLEDGE"))))));
        });
    }
}
