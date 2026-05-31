/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;

/**
 * Zero-copy adapter that exposes a Netty {@link io.netty.handler.codec.http.HttpHeaders} as a
 * smithy-java {@link HttpHeaders} by reference, instead of copying every name/value pair into a new
 * {@code ArrayHttpHeaders} (which is what {@link NettyUtils#fromH1Headers} did).
 *
 * <p>Mirrors {@code JavaHttpHeaders} (which wraps the JDK client's headers): the hot accessors
 * ({@link #firstValue}, {@link #contentType()}, {@link #contentLength()}, {@link #hasHeader},
 * {@link #allValues}) delegate straight to Netty's already case-insensitive lookups, so the grouped
 * {@link #map()} is only materialized if a caller actually asks for it. {@link #forEachEntry}
 * iterates Netty's entries directly. Header names are lowercased (per the {@link HttpHeaders}
 * contract) only on the {@code map()}/{@code forEachEntry} paths; Netty preserves wire case in
 * iteration but matches case-insensitively on lookup.
 *
 * <h2>Lifetime</h2>
 * Safe to wrap: Netty's HTTP/1.1 {@code DefaultHttpHeaders} store decoded {@code String}/
 * {@code AsciiString} values, not pooled {@code ByteBuf} slices, so this wrapper does not pin a
 * reference-counted buffer and may outlive the connection's return to the pool. (The response
 * <em>body</em> ByteBufs are managed separately.)
 */
final class NettyH1Headers implements HttpHeaders {

    private final io.netty.handler.codec.http.HttpHeaders netty;
    private volatile Map<String, List<String>> materialized;

    NettyH1Headers(io.netty.handler.codec.http.HttpHeaders netty) {
        this.netty = netty;
    }

    @Override
    public List<String> allValues(String name) {
        return netty.getAll(name);
    }

    @Override
    public boolean hasHeader(String name) {
        return netty.contains(name);
    }

    @Override
    public boolean hasHeader(HeaderName name) {
        return netty.contains(name.name());
    }

    @Override
    public String firstValue(String name) {
        return netty.get(name);
    }

    @Override
    public String firstValue(HeaderName name) {
        return netty.get(name.name());
    }

    @Override
    public String contentType() {
        return netty.get(HttpHeaderNames.CONTENT_TYPE);
    }

    @Override
    public Long contentLength() {
        String value = netty.get(HttpHeaderNames.CONTENT_LENGTH);
        return value == null ? null : Long.parseLong(value);
    }

    @Override
    public int size() {
        return netty.size();
    }

    @Override
    public boolean isEmpty() {
        return netty.isEmpty();
    }

    @Override
    public Map<String, List<String>> map() {
        return materialize();
    }

    @Override
    public void forEachEntry(BiConsumer<String, String> consumer) {
        var it = netty.iteratorCharSequence();
        while (it.hasNext()) {
            var e = it.next();
            consumer.accept(canonicalize(e.getKey()), e.getValue().toString());
        }
    }

    @Override
    public <C> void forEachEntry(C contextValue, HeaderWithValueConsumer<C> consumer) {
        var it = netty.iteratorCharSequence();
        while (it.hasNext()) {
            var e = it.next();
            consumer.accept(contextValue, canonicalize(e.getKey()), e.getValue().toString());
        }
    }

    /**
     * Lazily build the grouped, lowercase-keyed, unmodifiable map only when a caller needs the full
     * {@link Map} view. The common response path (contentType/contentLength/firstValue) never gets
     * here.
     */
    private Map<String, List<String>> materialize() {
        var result = materialized;
        if (result != null) {
            return result;
        }
        var grouped = new LinkedHashMap<String, List<String>>(netty.size());
        var it = netty.iteratorCharSequence();
        while (it.hasNext()) {
            var e = it.next();
            grouped.computeIfAbsent(canonicalize(e.getKey()), k -> new ArrayList<>(1))
                    .add(e.getValue().toString());
        }
        result = Collections.unmodifiableMap(grouped);
        materialized = result;
        return result;
    }

    private static String canonicalize(CharSequence name) {
        // AsciiString.toString() caches its String; canonicalize maps known names to interned
        // lowercase constants and only allocates a lowercased String for unknown headers.
        return HeaderName.canonicalize(name instanceof AsciiString a ? a.toString() : name.toString());
    }
}
