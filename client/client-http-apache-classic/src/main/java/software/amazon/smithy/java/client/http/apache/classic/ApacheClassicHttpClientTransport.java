/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.util.Timeout;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Synchronous Apache HttpClient 5 Classic transport.
 *
 * <p>Uses Apache's blocking I/O HttpClient. With virtual threads, blocking on the socket
 * read parks the VT instead of holding a kernel thread, so the simpler classic API matches
 * VT semantics better than the async/reactive variant.
 *
 * <p>HTTP/1.1 only — Apache HC5 Classic does not support HTTP/2.
 */
public final class ApacheClassicHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private final CloseableHttpClient client;

    public ApacheClassicHttpClientTransport() {
        this(defaultClient(20, 20));
    }

    public ApacheClassicHttpClientTransport(int maxConnections, int maxConnectionsPerRoute) {
        this(defaultClient(maxConnections, maxConnectionsPerRoute));
    }

    public ApacheClassicHttpClientTransport(CloseableHttpClient client) {
        this.client = client;
    }

    private static CloseableHttpClient defaultClient(int maxTotal, int maxPerRoute) {
        var connMgr = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotal)
                .setMaxConnPerRoute(maxPerRoute)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connMgr)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                        .setResponseTimeout(Timeout.ofSeconds(60))
                        .build())
                .disableAutomaticRetries()
                .disableContentCompression()
                .disableRedirectHandling()
                .build();
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        try {
            HttpUriRequestBase apacheReq = new HttpUriRequestBase(request.method(), request.uri().toURI());
            // Apache derives content-length, content-type, and transfer-encoding from the entity;
            // forwarding them here would double-set and cause "header already present" errors.
            request.headers().forEachEntry((name, value) -> {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (lower.equals("content-length") || lower.equals("content-type")
                        || lower.equals("transfer-encoding")
                        || lower.equals("host")) {
                    return;
                }
                apacheReq.addHeader(name, value);
            });

            DataStream body = request.body();
            if (body != null && body.contentLength() != 0) {
                apacheReq.setEntity(new DataStreamHttpEntity(body));
            }

            @SuppressWarnings("deprecation")
            CloseableHttpResponse response = client.execute(apacheReq);
            boolean returnResponse = false;
            try {
                int status = response.getCode();
                Map<String, List<String>> respHeaders = new LinkedHashMap<>();
                for (var h : response.getHeaders()) {
                    respHeaders.computeIfAbsent(h.getName().toLowerCase(java.util.Locale.ROOT),
                            k -> new ArrayList<>(1))
                            .add(h.getValue());
                }
                HttpHeaders headers = HttpHeaders.of(respHeaders);

                var entity = response.getEntity();
                if (entity == null || entity.getContentLength() == 0) {
                    return HttpResponse.of(HttpVersion.HTTP_1_1, status, headers, DataStream.ofEmpty());
                }

                String contentType = headers.firstValue("content-type");
                DataStream respBody = DataStream.ofInputStream(
                        new CloseResponseInputStream(entity.getContent(), response),
                        contentType,
                        entity.getContentLength());
                HttpResponse result = HttpResponse.of(HttpVersion.HTTP_1_1, status, headers, respBody);
                returnResponse = true;
                return result;
            } finally {
                if (!returnResponse) {
                    response.close();
                }
            }
        } catch (IOException e) {
            throw ClientTransport.remapExceptions(e);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private static final class DataStreamHttpEntity extends AbstractHttpEntity {
        private final DataStream body;

        DataStreamHttpEntity(DataStream body) {
            super(body.contentType() != null ? ContentType.parse(body.contentType()) : null,
                    null,
                    false);
            this.body = body;
        }

        @Override
        public boolean isRepeatable() {
            return body.isReplayable();
        }

        @Override
        public long getContentLength() {
            return body.contentLength();
        }

        @Override
        public InputStream getContent() {
            return body.asInputStream();
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            body.writeTo(out);
        }

        @Override
        public boolean isStreaming() {
            return !body.isReplayable();
        }

        @Override
        public void close() {}
    }

    private static final class CloseResponseInputStream extends InputStream {
        private final InputStream delegate;
        private final CloseableHttpResponse response;
        private boolean closed;

        CloseResponseInputStream(InputStream delegate, CloseableHttpResponse response) {
            this.delegate = delegate;
            this.response = response;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            return delegate.transferTo(out);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException thrown = null;

            try {
                delegate.close();
            } catch (IOException e) {
                thrown = e;
            }

            try {
                response.close();
            } catch (IOException e) {
                if (thrown == null) {
                    thrown = e;
                } else {
                    thrown.addSuppressed(e);
                }
            }

            if (thrown != null) {
                throw thrown;
            }
        }
    }
}
