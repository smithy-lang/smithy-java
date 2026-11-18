/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.core.serde.document.Document;

public class ClientTransportFactoryTest {

    private static final String SERVICES = "META-INF/services/" + ClientTransportFactory.class.getName();

    @TempDir
    Path tempDir;

    @Test
    public void loadsFactoriesByDescendingPriority() throws IOException {
        var services = tempDir.resolve("services");
        Files.writeString(
                services,
                String.join(
                        "\n",
                        LowFactory.class.getName(),
                        FirstDefaultFactory.class.getName(),
                        HighFactory.class.getName(),
                        SecondDefaultFactory.class.getName()),
                StandardCharsets.UTF_8);

        var factories = ClientTransportFactory.load(new ServicesClassLoader(services.toUri().toURL()));

        // Ties keep their service file order.
        assertThat(
                factories.stream().map(ClientTransportFactory::name).toList(),
                contains("high", "first-default", "second-default", "low"));
    }

    /** Serves a single services file for the transport factory SPI, and delegates class loading. */
    private static final class ServicesClassLoader extends ClassLoader {
        private final URL services;

        private ServicesClassLoader(URL services) {
            super(ClientTransportFactoryTest.class.getClassLoader());
            this.services = services;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return SERVICES.equals(name) ? Collections.enumeration(List.of(services)) : super.getResources(name);
        }
    }

    private abstract static class TestFactory implements ClientTransportFactory<Object, Object> {
        @Override
        public MessageExchange<Object, Object> messageExchange() {
            return new MessageExchange<>() {};
        }

        @Override
        public ClientTransport<Object, Object> createTransport(Document settings, Document pluginSettings) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class LowFactory extends TestFactory {
        @Override
        public String name() {
            return "low";
        }

        @Override
        public byte priority() {
            return -1;
        }
    }

    public static final class FirstDefaultFactory extends TestFactory {
        @Override
        public String name() {
            return "first-default";
        }
    }

    public static final class SecondDefaultFactory extends TestFactory {
        @Override
        public String name() {
            return "second-default";
        }
    }

    public static final class HighFactory extends TestFactory {
        @Override
        public String name() {
            return "high";
        }

        @Override
        public byte priority() {
            return 1;
        }
    }
}
