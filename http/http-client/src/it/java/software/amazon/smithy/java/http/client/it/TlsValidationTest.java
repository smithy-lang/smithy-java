/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests TLS certificate validation edge cases.
 */
public class TlsValidationTest {

    private static TestCertificateGenerator.CertificateBundle validBundle;
    private NettyTestServer server;
    private HttpClient client;

    @BeforeAll
    static void beforeAll() throws Exception {
        validBundle = TestCertificateGenerator.generateCertificates();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void rejectsUntrustedCertificate() throws Exception {
        // Server uses valid cert, but client doesn't trust the CA
        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new TextResponseHttp11ClientHandler("response"))
                .sslContextBuilder(SslContextBuilder.forServer(
                        validBundle.serverPrivateKey,
                        validBundle.serverCertificate))
                .build();
        server.start();

        // Client with empty trust store (doesn't trust any CA)
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, new SecureRandom()); // Default trust manager won't trust test CA

        client = createClient(sslContext);
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "https://localhost:" + server.getPort(),
                "");

        var ex = assertThrows(IOException.class, () -> client.send(request));
        assertTlsFailure(ex);
    }

    @Test
    void rejectsWrongHostname() throws Exception {
        // Generate cert for "wronghost.example.com" instead of "localhost"
        var wrongHostBundle = generateCertForHost("wronghost.example.com");

        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new TextResponseHttp11ClientHandler("response"))
                .sslContextBuilder(SslContextBuilder.forServer(
                        wrongHostBundle.serverPrivateKey,
                        wrongHostBundle.serverCertificate))
                .build();
        server.start();

        // Client trusts the CA but hostname won't match
        client = createClient(createTrustingSslContext(wrongHostBundle.caCertificate));
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "https://localhost:" + server.getPort(),
                "");

        var ex = assertThrows(IOException.class, () -> client.send(request));
        assertTlsFailure(ex);
    }

    @Test
    void rejectsExpiredCertificate() throws Exception {
        // Generate expired cert
        var expiredBundle = generateExpiredCert();

        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new TextResponseHttp11ClientHandler("response"))
                .sslContextBuilder(SslContextBuilder.forServer(
                        expiredBundle.serverPrivateKey,
                        expiredBundle.serverCertificate))
                .build();
        server.start();

        client = createClient(createTrustingSslContext(expiredBundle.caCertificate));
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "https://localhost:" + server.getPort(),
                "");

        var ex = assertThrows(IOException.class, () -> client.send(request));
        assertTlsFailure(ex);
    }

    @Test
    void rejectsSelfSignedCertificate() throws Exception {
        // Generate self-signed cert (no CA)
        var selfSignedCert = generateSelfSignedCert();

        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new TextResponseHttp11ClientHandler("response"))
                .sslContextBuilder(SslContextBuilder.forServer(
                        selfSignedCert.privateKey,
                        selfSignedCert.certificate))
                .build();
        server.start();

        // Client with default trust store (won't trust self-signed)
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, new SecureRandom());

        client = createClient(sslContext);
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "https://localhost:" + server.getPort(),
                "");

        var ex = assertThrows(IOException.class, () -> client.send(request));
        assertTlsFailure(ex);
    }

    private void assertTlsFailure(Throwable ex) {
        // TLS failures can manifest as SSLHandshakeException or SocketException (socket closed by peer)
        // depending on timing of when the failure is detected
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SSLHandshakeException
                    || (current instanceof java.net.SocketException
                            && current.getMessage() != null
                            && current.getMessage().contains("closed"))) {
                return;
            }
            current = current.getCause();
        }
        throw new AssertionError("Expected TLS failure (SSLHandshakeException or SocketException) but not found in: "
                + ex, ex);
    }

    private HttpClient createClient(SSLContext sslContext) {
        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));
        return HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .maxConnectionsPerRoute(10)
                        .maxTotalConnections(10)
                        .maxIdleTime(Duration.ofMinutes(1))
                        .dnsResolver(staticDns)
                        .sslContext(sslContext)
                        .build())
                .build();
    }

    private SSLContext createTrustingSslContext(X509Certificate caCert) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca-cert", caCert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    private TestCertificateGenerator.CertificateBundle generateCertForHost(String hostname) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);

        KeyPair caKeyPair = keyGen.generateKeyPair();
        X509Certificate caCert = generateCA(caKeyPair);

        KeyPair serverKeyPair = keyGen.generateKeyPair();
        X509Certificate serverCert = generateServerCert(serverKeyPair,
                caKeyPair,
                caCert,
                hostname,
                new Date(),
                new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));

        return new TestCertificateGenerator.CertificateBundle(caCert, serverCert, serverKeyPair.getPrivate());
    }

    private TestCertificateGenerator.CertificateBundle generateExpiredCert() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);

        KeyPair caKeyPair = keyGen.generateKeyPair();
        X509Certificate caCert = generateCA(caKeyPair);

        KeyPair serverKeyPair = keyGen.generateKeyPair();
        // Expired: notBefore and notAfter both in the past
        Date notBefore = new Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000);
        X509Certificate serverCert = generateServerCert(serverKeyPair,
                caKeyPair,
                caCert,
                "localhost",
                notBefore,
                notAfter);

        return new TestCertificateGenerator.CertificateBundle(caCert, serverCert, serverKeyPair.getPrivate());
    }

    private SelfSignedCert generateSelfSignedCert() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        var subject = new X500Name("CN=localhost, O=Test, C=US");
        var certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.currentTimeMillis()),
                new Date(),
                new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
                subject,
                keyPair.getPublic());

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

        return new SelfSignedCert(cert, keyPair.getPrivate());
    }

    private X509Certificate generateCA(KeyPair keyPair) throws Exception {
        var issuer = new X500Name("CN=Test CA, O=Test, C=US");
        var certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.currentTimeMillis()),
                new Date(),
                new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
                issuer,
                keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
    }

    private X509Certificate generateServerCert(
            KeyPair serverKeyPair,
            KeyPair caKeyPair,
            X509Certificate caCert,
            String hostname,
            Date notBefore,
            Date notAfter
    ) throws Exception {
        var issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
        var subject = new X500Name("CN=" + hostname + ", O=Test, C=US");

        var sanNames = new GeneralName[] {new GeneralName(GeneralName.dNSName, hostname)};

        var certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.currentTimeMillis()),
                notBefore,
                notAfter,
                subject,
                serverKeyPair.getPublic())
                .addExtension(Extension.subjectAlternativeName, false, new GeneralNames(sanNames));

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
    }

    private record SelfSignedCert(X509Certificate certificate, java.security.PrivateKey privateKey) {}
}
