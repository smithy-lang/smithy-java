/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NettHttpHeadersTest {
    @Test
    public void convertsToIterator() {
        var netty = new DefaultHttpHeaders();
        netty.set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        netty.add("foo", List.of("A", "B"));

        var wrapped = new NettyHttpHeaders(netty);
        Set<String> names = new HashSet<>();
        for (var entry : wrapped) {
            names.add(entry.getKey());
            if (entry.getKey().equals("content-type")) {
                assertThat(entry.getValue(), contains("text/plain"));
            } else if (entry.getKey().equals("foo")) {
                assertThat(entry.getValue(), contains("A", "B"));
            } else {
                Assertions.fail("Expected content-type or foo, found " + entry.getKey());
            }
        }

        assertThat(names, containsInAnyOrder("content-type", "foo"));
    }

    @Test
    public void convertsToMap() {
        var netty = new DefaultHttpHeaders();
        netty.set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        netty.add("foo", List.of("A", "B"));

        var wrapped = new NettyHttpHeaders(netty);
        var map = wrapped.map();

        assertThat(map.size(), equalTo(2));
        assertThat(map.entrySet(), hasSize(2));
        assertThat(map, hasKey("content-type"));
        assertThat(map, hasKey("foo"));
        assertThat(map.get("content-type"), equalTo(List.of("text/plain")));
        assertThat(map.get("foo"), equalTo(List.of("A", "B")));
        assertThat(map.get("blah"), nullValue());

        Set<String> names = new HashSet<>();
        for (var entry : map.entrySet()) {
            names.add(entry.getKey());
            if (entry.getKey().equals("content-type")) {
                assertThat(entry.getValue(), contains("text/plain"));
            } else if (entry.getKey().equals("foo")) {
                assertThat(entry.getValue(), contains("A", "B"));
            } else {
                Assertions.fail("Expected content-type or foo, found " + entry.getKey());
            }
        }

        assertThat(names, containsInAnyOrder("content-type", "foo"));
    }
}
