/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * A {@link ModifiableHttpHeaders} whose storage IS a Netty {@link io.netty.handler.codec.http.HttpHeaders}.
 *
 * <p>This is the write-side counterpart to the read-only {@link NettyH1Headers}. A transport vends it
 * via {@link NettyHttpRequestFactory} so the protocol serializes request headers directly into the
 * Netty container; at send time the transport reuses that same container by reference, with no
 * smithy&rarr;Netty marshalling copy (see {@link NettyUtils#fillH1Headers}).
 *
 * <h2>Case normalization</h2>
 * The {@link HttpHeaders} contract requires lowercase names from {@link #map()}/{@link #forEachEntry}.
 * Netty matches case-insensitively on lookup but preserves wire case in iteration, so those two
 * methods canonicalize to lowercase via {@link HeaderName#canonicalize}. This is REQUIRED for
 * correct SigV4 canonical-request/SignedHeaders computation.
 *
 * <h2>Mutation semantics</h2>
 * {@code addHeader} maps to Netty {@code add} (append), {@code setHeader}/{@code removeHeader}/
 * {@code clear} to the corresponding Netty operations. {@code toModifiable()} returns {@code this};
 * {@code copy()}/{@code toUnmodifiable()} are overridden to keep the Netty backing rather than
 * silently degrading to an array-backed copy. Lifetime is safe: a {@link DefaultHttpHeaders} holds
 * decoded {@code String}/{@code AsciiString} values, not pooled {@code ByteBuf} slices.
 */
final class NettyModifiableH1Headers implements ModifiableHttpHeaders {

    private final io.netty.handler.codec.http.HttpHeaders netty;

    NettyModifiableH1Headers() {
        // validateHeaders=false: the codec re-validates on encode, and the protocol/SigV4 supply
        // already-valid names/values; skipping per-add validation avoids redundant work.
        this(new DefaultHttpHeaders(false));
    }

    NettyModifiableH1Headers(io.netty.handler.codec.http.HttpHeaders netty) {
        this.netty = netty;
    }

    /** The backing Netty headers, for the transport's zero-copy send path. */
    io.netty.handler.codec.http.HttpHeaders nettyHeaders() {
        return netty;
    }

    // ---- writes ----

    @Override
    public void addHeader(String name, String value) {
        netty.add(name, value);
    }

    @Override
    public void addHeader(String name, List<String> values) {
        netty.add(name, values);
    }

    @Override
    public void setHeader(String name, String value) {
        netty.set(name, value);
    }

    @Override
    public void setHeader(String name, List<String> values) {
        netty.set(name, values);
    }

    @Override
    public void removeHeader(String name) {
        netty.remove(name);
    }

    @Override
    public void clear() {
        netty.clear();
    }

    // ---- reads ----

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
        var grouped = new LinkedHashMap<String, List<String>>(netty.size());
        var it = netty.iteratorCharSequence();
        while (it.hasNext()) {
            var e = it.next();
            grouped.computeIfAbsent(HeaderName.canonicalize(e.getKey().toString()), k -> new ArrayList<>(1))
                    .add(e.getValue().toString());
        }
        return Collections.unmodifiableMap(grouped);
    }

    @Override
    public void forEachEntry(BiConsumer<String, String> consumer) {
        var it = netty.iteratorCharSequence();
        while (it.hasNext()) {
            var e = it.next();
            consumer.accept(HeaderName.canonicalize(e.getKey().toString()), e.getValue().toString());
        }
    }

    @Override
    public <C> void forEachEntry(C contextValue, HeaderWithValueConsumer<C> consumer) {
        var it = netty.iteratorCharSequence();
        while (it.hasNext()) {
            var e = it.next();
            consumer.accept(contextValue, HeaderName.canonicalize(e.getKey().toString()), e.getValue().toString());
        }
    }

    // ---- conversions: keep the Netty backing instead of degrading to array headers ----

    @Override
    public ModifiableHttpHeaders toModifiable() {
        return this;
    }

    @Override
    public ModifiableHttpHeaders copy() {
        return new NettyModifiableH1Headers(new DefaultHttpHeaders(false).add(netty));
    }

    @Override
    public HttpHeaders toUnmodifiable() {
        // The send path consumes this directly; a defensive immutable view is not needed and would
        // force a copy. Returning self preserves the zero-copy backing (the request is treated as
        // effectively immutable once built).
        return this;
    }

    @Override
    public String toString() {
        return "NettyModifiableH1Headers" + netty;
    }
}
