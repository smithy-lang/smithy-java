/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

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

    @Override
    public Map<String, List<String>> map() {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<String, List<String>>> entrySet() {
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<String, List<String>>> iterator() {
                        return NettyHttpHeaders.this.iterator();
                    }

                    @Override
                    public int size() {
                        return nettyHeaders.names().size();
                    }
                };
            }

            @Override
            public List<String> get(Object key) {
                // To mimic a normal map, return null when the header doesn't exist.
                var result = nettyHeaders.getAll((String) key);
                return result.isEmpty() ? null : result;
            }

            @Override
            public boolean containsKey(Object key) {
                return nettyHeaders.contains((String) key);
            }

            @Override
            public int size() {
                return nettyHeaders.names().size();
            }

            @Override
            public Set<String> keySet() {
                return nettyHeaders.names();
            }
        };
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return new Iterator<>() {
            private final Iterator<String> iter = nettyHeaders.names().iterator();

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Map.Entry<String, List<String>> next() {
                var next = iter.next();
                return new AbstractMap.SimpleImmutableEntry<>(next, nettyHeaders.getAll(next));
            }
        };
    }

    HttpHeaders getNettyHeaders() {
        return nettyHeaders;
    }
}
