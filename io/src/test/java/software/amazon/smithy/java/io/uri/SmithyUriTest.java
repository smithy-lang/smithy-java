/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class SmithyUriTest {
    @Nested
    class OfString {
        static Stream<Arguments> validUris() {
            return Stream.of(
                    Arguments.of("https://example.com", "https", "example.com", -1, "", null),
                    Arguments.of("https://example.com/", "https", "example.com", -1, "/", null),
                    Arguments.of("https://example.com:8080/path", "https", "example.com", 8080, "/path", null),
                    Arguments.of("http://host/a/b?x=1&y=2", "http", "host", -1, "/a/b", "x=1&y=2"),
                    Arguments.of("http://host/a%20b", "http", "host", -1, "/a%20b", null),
                    Arguments.of("http://host/?q=hello%20world", "http", "host", -1, "/", "q=hello%20world"),
                    Arguments.of("http://host:443/path?q=v", "http", "host", 443, "/path", "q=v"));
        }

        @ParameterizedTest
        @MethodSource("validUris")
        void parsesComponents(String input, String scheme, String host, int port, String path, String query) {
            var uri = SmithyUri.of(input);
            assertThat(uri.getScheme(), equalTo(scheme));
            assertThat(uri.getHost(), equalTo(host));
            assertThat(uri.getPort(), equalTo(port));
            assertThat(uri.getPath(), equalTo(path));
            assertThat(uri.getQuery(), equalTo(query));
        }

        @ParameterizedTest
        @MethodSource("validUris")
        void toStringRoundTrips(String input, String scheme, String host, int port, String path, String query) {
            var uri = SmithyUri.of(input);
            // Parsing the toString should yield an equivalent URI
            assertThat(URI.create(uri.toString()).getScheme(), equalTo(scheme));
            assertThat(URI.create(uri.toString()).getHost(), equalTo(host));
        }

        @ParameterizedTest
        @ValueSource(strings = {"://missing-scheme", "not a uri at all", "http://host/path with spaces"})
        void rejectsInvalidStrings(String input) {
            assertThrows(IllegalArgumentException.class, () -> SmithyUri.of(input));
        }

        @Test
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> SmithyUri.of((String) null));
        }
    }

    @Nested
    class OfUri {
        @Test
        void preservesComponents() {
            var javaUri = URI.create("https://example.com:8443/foo/bar?baz=qux");
            var uri = SmithyUri.of(javaUri);
            assertThat(uri.getScheme(), equalTo("https"));
            assertThat(uri.getHost(), equalTo("example.com"));
            assertThat(uri.getPort(), equalTo(8443));
            assertThat(uri.getPath(), equalTo("/foo/bar"));
            assertThat(uri.getQuery(), equalTo("baz=qux"));
        }

        @Test
        void cachesOriginalUri() {
            var javaUri = URI.create("https://example.com/path");
            var uri = SmithyUri.of(javaUri);
            // toURI should return the cached instance
            assertThat(uri.toURI(), is(javaUri));
        }

        @Test
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> SmithyUri.of((URI) null));
        }
    }

    @Nested
    class OfComponents {
        @Test
        void basicConstruction() {
            var uri = SmithyUri.of("https", "example.com", 443, "/path", "key=val");
            assertThat(uri.getScheme(), equalTo("https"));
            assertThat(uri.getHost(), equalTo("example.com"));
            assertThat(uri.getPort(), equalTo(443));
            assertThat(uri.getPath(), equalTo("/path"));
            assertThat(uri.getQuery(), equalTo("key=val"));
        }

        @Test
        void nullPathDefaultsToEmpty() {
            var uri = SmithyUri.of("https", "host", -1, null, null);
            assertThat(uri.getPath(), equalTo(""));
        }

        @Test
        void emptyPathStaysEmpty() {
            var uri = SmithyUri.of("https", "host", -1, "", null);
            assertThat(uri.getPath(), equalTo(""));
        }

        @Test
        void nullSchemeAndHostAllowed() {
            var uri = SmithyUri.of(null, null, -1, "/path", null);
            assertThat(uri.getScheme(), nullValue());
            assertThat(uri.getHost(), nullValue());
            assertThat(uri.toString(), equalTo("/path"));
        }

        @Test
        void rejectsInvalidScheme() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("123bad", "host", -1, "/", null));
        }

        @Test
        void rejectsInvalidHost() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host name", -1, "/", null));
        }

        @Test
        void rejectsInvalidPort() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", 70000, "/", null));
        }

        @Test
        void rejectsInvalidPath() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", -1, "/path with space", null));
        }

        @Test
        void rejectsInvalidQuery() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", -1, "/", "key=val ue"));
        }

        @Test
        void rejectsBadPercentEncodingInPath() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", -1, "/foo%G1", null));
        }

        @Test
        void rejectsIncompletePercentEncodingInQuery() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", -1, "/", "key=val%2"));
        }

        @Test
        void rejectsFragmentCharInPath() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", -1, "/foo#bar", null));
        }

        @Test
        void rejectsFragmentCharInQuery() {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", -1, "/", "foo#bar"));
        }

        @Test
        void acceptsValidPercentEncoding() {
            var uri = SmithyUri.of("https", "host", -1, "/a%20b", "x=%2F");
            assertThat(uri.getPath(), equalTo("/a%20b"));
            assertThat(uri.getQuery(), equalTo("x=%2F"));
        }
    }

    @Nested
    class Withers {
        private final SmithyUri base = SmithyUri.of("https://example.com:8080/path?q=1");

        @Test
        void withScheme() {
            var uri = base.withScheme("http");
            assertThat(uri.getScheme(), equalTo("http"));
            assertThat(uri.getHost(), equalTo("example.com"));
            assertThat(uri.getPort(), equalTo(8080));
            assertThat(uri.getPath(), equalTo("/path"));
            assertThat(uri.getQuery(), equalTo("q=1"));
        }

        @Test
        void withHost() {
            var uri = base.withHost("other.com");
            assertThat(uri.getHost(), equalTo("other.com"));
            assertThat(uri.getScheme(), equalTo("https"));
        }

        @Test
        void withPort() {
            var uri = base.withPort(9090);
            assertThat(uri.getPort(), equalTo(9090));
        }

        @Test
        void withPath() {
            var uri = base.withPath("/new/path");
            assertThat(uri.getPath(), equalTo("/new/path"));
            assertThat(uri.getQuery(), equalTo("q=1"));
        }

        @Test
        void withQuery() {
            var uri = base.withQuery("a=b&c=d");
            assertThat(uri.getQuery(), equalTo("a=b&c=d"));
            assertThat(uri.getPath(), equalTo("/path"));
        }

        @Test
        void withNullQuery() {
            var uri = base.withQuery(null);
            assertThat(uri.getQuery(), nullValue());
        }

        @Test
        void witherInvalidatesCache() {
            // Access toString to populate cache
            var original = base.toString();
            var modified = base.withHost("changed.com");
            assertThat(modified.toString(), not(equalTo(original)));
        }

        @Test
        void withSchemeReturnsSameWhenEqual() {
            assertThat(base.withScheme("https"), is(base));
        }

        @Test
        void withHostReturnsSameWhenEqual() {
            assertThat(base.withHost("example.com"), is(base));
        }

        @Test
        void withPortReturnsSameWhenEqual() {
            assertThat(base.withPort(8080), is(base));
        }

        @Test
        void withPathReturnsSameWhenEqual() {
            assertThat(base.withPath("/path"), is(base));
        }

        @Test
        void withQueryReturnsSameWhenEqual() {
            assertThat(base.withQuery("q=1"), is(base));
        }

        @Test
        void withNullSchemeReturnsSameWhenAlreadyNull() {
            var noScheme = SmithyUri.of(null, null, -1, "/path", null);
            assertThat(noScheme.withScheme(null), is(noScheme));
        }

        @Test
        void witherValidatesScheme() {
            assertThrows(IllegalArgumentException.class, () -> base.withScheme("1bad"));
        }

        @Test
        void witherValidatesHost() {
            assertThrows(IllegalArgumentException.class, () -> base.withHost("bad host"));
        }

        @Test
        void witherValidatesPort() {
            assertThrows(IllegalArgumentException.class, () -> base.withPort(-2));
        }

        @Test
        void witherValidatesPath() {
            assertThrows(IllegalArgumentException.class, () -> base.withPath("/bad path"));
        }

        @Test
        void witherValidatesQuery() {
            assertThrows(IllegalArgumentException.class, () -> base.withQuery("bad query"));
        }
    }

    @Nested
    class CompoundOps {

        static Stream<Arguments> concatPathCases() {
            return Stream.of(
                    Arguments.of("/base", "/suffix", "/base/suffix"),
                    Arguments.of("/base/", "/suffix", "/base/suffix"),
                    Arguments.of("/base", "suffix", "/base/suffix"),
                    Arguments.of("/base/", "suffix", "/base/suffix"),
                    Arguments.of("/", "/suffix", "/suffix"),
                    Arguments.of("/", "suffix", "/suffix"),
                    Arguments.of("/base", "/", "/base/"),
                    Arguments.of("/base", "", "/base"),
                    Arguments.of("/base", null, "/base"));
        }

        @ParameterizedTest
        @MethodSource("concatPathCases")
        void withConcatPath(String basePath, String suffix, String expected) {
            var uri = SmithyUri.of("https", "host", -1, basePath, null);
            var result = uri.withConcatPath(suffix);
            assertThat(result.getPath(), equalTo(expected));
        }

        @Test
        void withConcatPathReturnsSameInstanceForNullSuffix() {
            var uri = SmithyUri.of("https://host/path");
            assertThat(uri.withConcatPath(null), is(uri));
        }

        @Test
        void withConcatPathReturnsSameInstanceForEmptySuffix() {
            var uri = SmithyUri.of("https://host/path");
            assertThat(uri.withConcatPath(""), is(uri));
        }

        @Test
        void withEndpoint() {
            var request = SmithyUri.of(null, null, -1, "/operation/Foo", "bar=baz");
            var endpoint = SmithyUri.of("https://service.example.com:8443/v1");
            var result = request.withEndpoint(endpoint);

            assertThat(result.getScheme(), equalTo("https"));
            assertThat(result.getHost(), equalTo("service.example.com"));
            assertThat(result.getPort(), equalTo(8443));
            assertThat(result.getPath(), equalTo("/v1/operation/Foo"));
            assertThat(result.getQuery(), equalTo("bar=baz"));
        }

        @Test
        void withEndpointRootPath() {
            var request = SmithyUri.of(null, null, -1, "/op", null);
            var endpoint = SmithyUri.of("https://host/");
            var result = request.withEndpoint(endpoint);
            assertThat(result.getPath(), equalTo("/op"));
        }

        @Test
        void withEndpointRejectsNull() {
            var uri = SmithyUri.of("https://host/path");
            assertThrows(NullPointerException.class, () -> uri.withEndpoint(null));
        }
    }

    @Nested
    class NormalizedPath {
        static Stream<Arguments> cases() {
            return Stream.of(
                    // Simple paths — no normalization needed
                    Arguments.of("/"),
                    Arguments.of("/foo"),
                    Arguments.of("/foo/bar"),
                    Arguments.of("/foo/bar/"),
                    // Dot segments
                    Arguments.of("/foo/./bar"),
                    Arguments.of("/foo/../bar"),
                    Arguments.of("/foo/bar/./baz/../qux"),
                    Arguments.of("/./foo"),
                    Arguments.of("/../foo"),
                    Arguments.of("/foo/.."),
                    Arguments.of("/foo/."),
                    // Double slashes
                    Arguments.of("//"),
                    Arguments.of("//foo"),
                    Arguments.of("/foo//bar"),
                    Arguments.of("/foo//bar//baz"),
                    Arguments.of("//foo//"),
                    // Combined
                    Arguments.of("/foo/./bar/../baz"),
                    Arguments.of("/foo//./bar//../baz"),
                    Arguments.of("/a/b/c/../../d"),
                    Arguments.of("/a/b/../../../c"),
                    // Trailing slashes with dots
                    Arguments.of("/foo/bar/./"),
                    Arguments.of("/foo/bar/../"),
                    // Multiple dots (not special)
                    Arguments.of("/foo/.../bar"),
                    Arguments.of("/foo/..bar/baz"),
                    // Edge cases
                    Arguments.of(""),
                    Arguments.of("/foo/bar/baz/../../qux/./quux"));
        }

        @ParameterizedTest
        @MethodSource("cases")
        void matchesJavaUriNormalize(String path) {
            // URI.normalize() needs a host prefix to avoid "//" being parsed as authority
            var javaResult = URI.create("http://h" + path).normalize().getRawPath();
            var smithyUri = SmithyUri.of("http", "host", -1, path, null);
            assertThat("normalizedPath for: " + path, smithyUri.getNormalizedPath(), equalTo(javaResult));
        }

        @Test
        void isCached() {
            var uri = SmithyUri.of("http", "host", -1, "/foo/../bar", null);
            var first = uri.getNormalizedPath();
            var second = uri.getNormalizedPath();
            assertThat(Objects.equals(first, second), is(true));
        }

        @Test
        void returnsOriginalWhenAlreadyNormalized() {
            var uri = SmithyUri.of("http", "host", -1, "/foo/bar", null);
            assertThat(Objects.equals(uri.getNormalizedPath(), uri.getPath()), is(true));
        }
    }

    @Nested
    class DerivedValues {

        static Stream<Arguments> toStringCases() {
            return Stream.of(
                    Arguments.of("https", "example.com", -1, "", null, "https://example.com"),
                    Arguments.of("https", "example.com", 8080, "/path", "q=1", "https://example.com:8080/path?q=1"),
                    Arguments.of("http", "host", -1, "/a/b", null, "http://host/a/b"),
                    Arguments.of(null, null, -1, "/path", null, "/path"),
                    Arguments.of("https", "host", -1, "", "a=b", "https://host?a=b"));
        }

        @ParameterizedTest
        @MethodSource("toStringCases")
        void toStringFormat(String scheme, String host, int port, String path, String query, String expected) {
            var uri = SmithyUri.of(scheme, host, port, path, query);
            assertThat(uri.toString(), equalTo(expected));
        }

        @Test
        void toStringIsCached() {
            var uri = SmithyUri.of("https://example.com/path");
            var first = uri.toString();
            var second = uri.toString();
            // Same reference
            assertThat(Objects.equals(first, second), is(true));
        }

        @Test
        void toUriProducesValidUri() {
            var uri = SmithyUri.of("https", "example.com", 8080, "/a%20b", "x=%2F");
            var javaUri = uri.toURI();
            assertThat(javaUri.getScheme(), equalTo("https"));
            assertThat(javaUri.getHost(), equalTo("example.com"));
            assertThat(javaUri.getPort(), equalTo(8080));
            assertThat(javaUri.getRawPath(), equalTo("/a%20b"));
            assertThat(javaUri.getRawQuery(), equalTo("x=%2F"));
        }

        @Test
        void toUriIsCached() {
            var uri = SmithyUri.of("https://example.com/path");
            var first = uri.toURI();
            var second = uri.toURI();
            assertThat(first == second, is(true));
        }

        @Test
        void equalsSameUri() {
            var a = SmithyUri.of("https://example.com/path?q=1");
            var b = SmithyUri.of("https", "example.com", -1, "/path", "q=1");
            assertThat(a, equalTo(b));
            assertThat(a.hashCode(), equalTo(b.hashCode()));
        }

        @Test
        void notEqualsDifferentUri() {
            var a = SmithyUri.of("https://example.com/path");
            var b = SmithyUri.of("https://example.com/other");
            assertThat(a, not(equalTo(b)));
        }
    }

    @Nested
    class ValidationEdgeCases {

        static Stream<Arguments> validSchemes() {
            return Stream.of(
                    Arguments.of("http"),
                    Arguments.of("https"),
                    Arguments.of("h2c"),
                    Arguments.of("a+b-c.d"));
        }

        @ParameterizedTest
        @MethodSource("validSchemes")
        void acceptsValidSchemes(String scheme) {
            var uri = SmithyUri.of(scheme, "host", -1, "/", null);
            assertThat(uri.getScheme(), equalTo(scheme));
        }

        static Stream<Arguments> invalidSchemes() {
            return Stream.of(
                    Arguments.of(""),
                    Arguments.of("1http"),
                    Arguments.of("ht tp"),
                    Arguments.of("ht@p"));
        }

        @ParameterizedTest
        @MethodSource("invalidSchemes")
        void rejectsInvalidSchemes(String scheme) {
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of(scheme, "host", -1, "/", null));
        }

        @Test
        void portBoundaries() {
            SmithyUri.of("https", "host", 0, "/", null);
            SmithyUri.of("https", "host", 65535, "/", null);
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", -2, "/", null));
            assertThrows(IllegalArgumentException.class,
                    () -> SmithyUri.of("https", "host", 65536, "/", null));
        }

        @Test
        void hostWithBracketedIpv6() {
            var uri = SmithyUri.of("https", "[::1]", 8080, "/", null);
            assertThat(uri.getHost(), equalTo("[::1]"));
        }
    }
}
