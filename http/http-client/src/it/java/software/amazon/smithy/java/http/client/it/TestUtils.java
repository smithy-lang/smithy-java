/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import io.netty.handler.ssl.SslContextBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.io.datastream.DataStream;

public class TestUtils {
    static final List<String> IPSUM_LOREM = getIpsumLorem();

    private TestUtils() {}

    public static HttpRequest plainTextHttp11Request(String uri, String contents) {
        return plainTextRequest(HttpVersion.HTTP_1_1, uri, contents);
    }

    public static HttpRequest plainTextHttp2Request(String uri, String contents) {
        return plainTextRequest(HttpVersion.HTTP_2, uri, contents);
    }

    public static HttpRequest plainTextRequest(HttpVersion version, String uri, String contents) {
        return request(version, uri, DataStream.ofString(contents));
    }

    public static DataStream streamingBody(Iterable<String> values) {
        return DataStream.ofPublisher(new StreamingPublisher(values), "text/plain", -1);
    }

    public static HttpRequest request(HttpVersion version, String uri, DataStream body) {
        try {
            var headers = HttpHeaders.ofModifiable();
            headers.addHeader("content-type", "text/plain");
            if (body.contentLength() >= 0) {
                headers.addHeader("content-length", Long.toString(body.contentLength()));
            }
            return HttpRequest.builder()
                    .httpVersion(version)
                    .uri(new URI(uri))
                    .headers(headers)
                    .method("POST")
                    .body(body)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static SslContextBuilder createServerSslContextBuilder(
            TestCertificateGenerator.CertificateBundle bundle
    ) throws Exception {
        return SslContextBuilder
                .forServer(bundle.serverPrivateKey, bundle.serverCertificate);
    }

    public static SSLContext createClientSslContext(
            TestCertificateGenerator.CertificateBundle bundle
    ) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, createTrustManager(bundle.caCertificate), new SecureRandom());
        return sslContext;
    }

    public static TrustManager[] createTrustManager(X509Certificate caCert) throws Exception {
        // Create a KeyStore and add the CA certificate
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null); // Initialize empty keystore
        keyStore.setCertificateEntry("ca-cert", caCert);

        // Initialize TrustManagerFactory with the KeyStore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        // Return the first TrustManager (typically there's only one)
        return tmf.getTrustManagers();
    }

    private static List<String> getIpsumLorem() {
        return Arrays.asList(
                "Lorem ipsum dolor sit amet, ",
                "consectetur adipiscing elit, sed do ",
                "eiusmod tempor incididunt ut ",
                "labore et dolore magna aliqua. ",
                "Ut enim ad minim veniam, quis ",
                "nostrud exercitation ullamco laboris ",
                "nisi ut ",
                "aliquip ex ea commodo consequat. ",
                "Duis aute irure dolor in ",
                "reprehenderit in voluptate velit esse ",
                "cillum dolore eu fugiat nulla ",
                "pariatur. Excepteur sint occaecat ",
                "cupidatat non proident, sunt in ",
                "culpa qui officia deserunt mollit ",
                "anim id est laborum.");
    }

    static class StreamingPublisher implements Flow.Publisher<ByteBuffer> {
        private final Iterable<String> values;

        StreamingPublisher(Iterable<String> values) {
            this.values = values;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new StreamingSubscription(values, subscriber));
        }
    }

    static class StreamingSubscription implements Flow.Subscription {
        private final Iterator<String> values;
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private boolean completed = false;

        StreamingSubscription(Iterable<String> values, Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.values = values.iterator();
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (completed) {
                return;
            }
            for (var idx = 0; idx < n && values.hasNext(); idx++) {
                var value = ByteBuffer.wrap(values.next().getBytes(StandardCharsets.UTF_8));
                subscriber.onNext(value);
            }
            if (!values.hasNext()) {
                completed = true;
                subscriber.onComplete();
            }
        }

        @Override
        public void cancel() {
            subscriber.onComplete();
        }
    }
}
