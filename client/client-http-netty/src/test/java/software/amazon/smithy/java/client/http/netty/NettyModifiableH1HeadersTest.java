/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * Contract tests for the Netty-backed writable headers. These guard the invariants the rest of the
 * stack (notably SigV4 canonicalization) relies on: a {@link NettyModifiableH1Headers} must behave
 * identically to the default array-backed {@link ModifiableHttpHeaders} — lowercase-canonical
 * iteration, case-insensitive lookup, set-replaces-add semantics, and multi-value preservation —
 * because the request flows through {@code setServiceEndpoint} and signing via the
 * {@code ModifiableHttpHeaders} interface alone.
 */
class NettyModifiableH1HeadersTest {

    @Test
    void forEachEntryEmitsLowercaseCanonicalNamesLikeArrayImpl() {
        // SigV4 computes SignedHeaders / the canonical request by iterating forEachEntry; the names
        // MUST be lowercase regardless of the wire case they were added with.
        var netty = new NettyModifiableH1Headers();
        netty.addHeader("X-Amz-Date", "20240101T000000Z");
        netty.addHeader("Content-Type", "application/json");
        netty.addHeader("HOST", "example.com");

        var array = HttpHeaders.ofModifiable();
        array.addHeader("X-Amz-Date", "20240101T000000Z");
        array.addHeader("Content-Type", "application/json");
        array.addHeader("HOST", "example.com");

        assertThat(collectNames(netty), equalTo(collectNames(array)));
        // Every emitted name is lowercase.
        for (String name : collectNames(netty)) {
            assertThat(name, equalTo(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    @Test
    void caseInsensitiveLookup() {
        var h = new NettyModifiableH1Headers();
        h.addHeader("X-Amz-Target", "DynamoDB_20120810.GetItem");
        assertThat(h.firstValue("x-amz-target"), equalTo("DynamoDB_20120810.GetItem"));
        assertThat(h.firstValue(HeaderName.of("x-amz-target")), equalTo("DynamoDB_20120810.GetItem"));
        assertThat(h.hasHeader("X-AMZ-TARGET"), is(true));
        assertThat(h.firstValue("absent"), is(nullValue()));
    }

    @Test
    void setReplacesAllExistingValues() {
        var h = new NettyModifiableH1Headers();
        h.addHeader("accept", "a");
        h.addHeader("accept", "b");
        assertThat(h.allValues("accept"), containsInAnyOrder("a", "b"));
        h.setHeader("accept", "c");
        assertThat(h.allValues("accept"), contains("c"));
    }

    @Test
    void multiValuePreservedAndSizeCountsEachValue() {
        var h = new NettyModifiableH1Headers();
        h.addHeader("x-multi", "1");
        h.addHeader("x-multi", "2");
        h.addHeader("solo", "x");
        assertThat(h.size(), equalTo(3));
        assertThat(h.allValues("x-multi"), contains("1", "2"));
        Map<String, List<String>> map = h.map();
        assertThat(map.get("x-multi"), contains("1", "2"));
        assertThat(map.get("solo"), contains("x"));
    }

    @Test
    void contentTypeAndLengthAccessors() {
        var h = new NettyModifiableH1Headers();
        h.setHeader("content-type", "application/cbor");
        h.setHeader("content-length", "42");
        assertThat(h.contentType(), equalTo("application/cbor"));
        assertThat(h.contentLength(), equalTo(42L));
    }

    @Test
    void removeAndClear() {
        var h = new NettyModifiableH1Headers();
        h.addHeader("a", "1");
        h.addHeader("b", "2");
        h.removeHeader("a");
        assertThat(h.hasHeader("a"), is(false));
        assertThat(h.hasHeader("b"), is(true));
        h.clear();
        assertThat(h.isEmpty(), is(true));
    }

    @Test
    void toModifiableReturnsSelfAndCopyPreservesNettyBacking() {
        var h = new NettyModifiableH1Headers();
        h.addHeader("a", "1");
        assertThat(h.toModifiable() == h, is(true));

        ModifiableHttpHeaders copy = h.copy();
        assertThat(copy, is(Matchers.instanceOf(NettyModifiableH1Headers.class)));
        // Independent: mutating the copy doesn't change the original.
        copy.addHeader("b", "2");
        assertThat(h.hasHeader("b"), is(false));
        assertThat(copy.allValues("a"), contains("1"));
    }

    private static List<String> collectNames(HttpHeaders headers) {
        List<String> names = new ArrayList<>();
        headers.forEachEntry((name, value) -> names.add(name));
        names.sort(null);
        return names;
    }
}
