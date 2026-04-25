/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * Shared utilities for Netty HTTP transport: SSL setup, header conversion.
 */
final class NettyUtils {
    private NettyUtils() {}

    static SslContext buildSslContext(String[] alpnProtocols, boolean trustAll) throws SSLException {
        var builder = SslContextBuilder.forClient()
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
        if (trustAll) {
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        }
        if (alpnProtocols != null && alpnProtocols.length > 0) {
            String fallback = alpnProtocols[alpnProtocols.length - 1];
            if (!ApplicationProtocolNames.HTTP_1_1.equals(fallback)
                    && !ApplicationProtocolNames.HTTP_2.equals(fallback)) {
                fallback = ApplicationProtocolNames.HTTP_1_1;
            }
            builder.applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    alpnProtocols));
        }
        return builder.build();
    }

    /**
     * Convert Smithy request headers + pseudo-headers into Netty HTTP/2 headers.
     */
    static Http2Headers toH2Headers(HttpRequest request) {
        var uri = request.uri();
        String path = uri.getPath();
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        String authority = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        var headers = new DefaultHttp2Headers()
                .method(request.method())
                .path(path)
                .scheme(uri.getScheme())
                .authority(authority);
        for (Map.Entry<String, List<String>> e : request.headers().map().entrySet()) {
            String name = e.getKey().toLowerCase(Locale.ROOT);
            // HTTP/2 forbids Connection, Transfer-Encoding, Upgrade, Keep-Alive, Proxy-Connection
            if (name.equals("connection") || name.equals("transfer-encoding")
                    || name.equals("upgrade")
                    || name.equals("keep-alive")
                    || name.equals("proxy-connection")
                    || name.equals("host")) {
                continue;
            }
            for (String v : e.getValue()) {
                headers.add(name, v);
            }
        }
        return headers;
    }

    /**
     * Convert Smithy headers + method/path into Netty HTTP/1.1 request headers.
     * Returns the headers for an {@code io.netty.handler.codec.http.HttpRequest}.
     */
    static void fillH1Headers(HttpRequest smithyRequest, io.netty.handler.codec.http.HttpHeaders out) {
        var uri = smithyRequest.uri();
        String authority = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        out.set(HttpHeaderNames.HOST, authority);
        for (Map.Entry<String, List<String>> e : smithyRequest.headers().map().entrySet()) {
            String name = e.getKey();
            for (String v : e.getValue()) {
                out.add(name, v);
            }
        }
    }

    /**
     * Convert Netty HTTP/1.1 response headers to Smithy {@link HttpHeaders}.
     */
    static ModifiableHttpHeaders fromH1Headers(io.netty.handler.codec.http.HttpHeaders in) {
        var out = HttpHeaders.ofModifiable(in.size());
        for (Map.Entry<String, String> e : in) {
            out.addHeader(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Convert Netty HTTP/2 response headers to Smithy {@link HttpHeaders}, skipping pseudo-headers.
     */
    static ModifiableHttpHeaders fromH2Headers(Http2Headers in) {
        var out = HttpHeaders.ofModifiable(in.size());
        for (Map.Entry<CharSequence, CharSequence> e : in) {
            String name = e.getKey().toString();
            if (name.startsWith(":"))
                continue;
            out.addHeader(name, e.getValue().toString());
        }
        return out;
    }
}
