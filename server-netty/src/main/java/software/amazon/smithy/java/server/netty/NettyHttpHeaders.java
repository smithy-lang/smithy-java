/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.http.api.ModifiableHttpHeaders;

final class NettyHttpHeaders implements ModifiableHttpHeaders {

    private final io.netty.handler.codec.http.HttpHeaders nettyHeaders;

    NettyHttpHeaders() {
        this.nettyHeaders = new DefaultHttpHeaders();
    }

    NettyHttpHeaders(io.netty.handler.codec.http.HttpHeaders nettyHeaders) {
        this.nettyHeaders = nettyHeaders;
    }

    @Override
    public String firstValue(String name) {
        return nettyHeaders.get(name);
    }

    @Override
    public List<String> allValues(String name) {
        return nettyHeaders.getAll(name);
    }

    @Override
    public void putHeader(String name, String value) {
        nettyHeaders.add(name, value);
    }

    @Override
    public void putHeader(String name, List<String> values) {
        nettyHeaders.add(name, values);
    }

    @Override
    public void removeHeader(String name) {
        nettyHeaders.remove(name);
    }

    @Override
    public boolean isEmpty() {
        return nettyHeaders.isEmpty();
    }

    @Override
    public int size() {
        return nettyHeaders.size();
    }

    //TODO implement an efficient toMap and iterator.

    @Override
    public Map<String, List<String>> map() {
        var map = new HashMap<String, List<String>>();
        for (var name : nettyHeaders.names()) {
            map.put(name, nettyHeaders.getAll(name));
        }
        return map;
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return map().entrySet().iterator();
    }

    HttpHeaders getNettyHeaders() {
        return nettyHeaders;
    }
}
