/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * A URI whose constant parts are validated once, so building one from interpolated values only has to
 * check the values.
 *
 * <p><strong>This is not an RFC 6570 URI Template implementation.</strong> It does not parse URI
 * Template expressions, support named variables or operators, or automatically percent-encode values.
 * It is a positional builder for efficiently concatenating prevalidated URI-component literals with
 * validated host and path values.
 *
 * <p>Built for endpoint resolution, where the same URI shape is rebuilt on every request with only a few
 * varying parts, such as a bucket or region. A template hoists validation of everything constant to
 * {@link Builder#build()} and scans only supplied values while copying them into the result.
 *
 * <p>This is sound because the component validators are position-independent character-class scans:
 * scanning the literals once and each value as it arrives accepts exactly the same strings as scanning
 * the concatenation. Rules that cross part boundaries — a host must not be entirely empty, brackets are
 * only valid around an entire IPv6-style host, and a percent-escape must not be cut short — are checked
 * while the parts are joined.
 *
 * <p>For example, this template builds an S3-style endpoint whose bucket, region, and object key vary:
 *
 * <pre>{@code
 * SmithyUriTemplate template = SmithyUriTemplate.builder()
 *         .scheme("https")
 *         .addHostPlaceholder()
 *         .addHostLiteral(".s3.")
 *         .addHostPlaceholder()
 *         .addHostLiteral(".amazonaws.com")
 *         .addPathLiteral("/")
 *         .addPathPlaceholder()
 *         .build();
 *
 * SmithyUri uri = template.expand("my-bucket", "us-west-2", "photos/cat.jpg");
 * // https://my-bucket.s3.us-west-2.amazonaws.com/photos/cat.jpg
 * }</pre>
 *
 * <p>Expansion values are positional: all host placeholders are supplied first, followed by all path
 * placeholders, in the order each was added.
 *
 * <p>Host placeholders are concatenated directly with the surrounding host literals. The template does
 * not insert dots or otherwise divide placeholders into labels:
 *
 * <pre>{@code
 * SmithyUriTemplate hostTemplate = SmithyUriTemplate.builder()
 *         .scheme("https")
 *         .addHostLiteral("api-")
 *         .addHostPlaceholder()
 *         .addHostLiteral(".")
 *         .addHostPlaceholder()
 *         .addHostLiteral(".example.com")
 *         .build();
 *
 * SmithyUri uri = hostTemplate.expand("blue", "us-west-2");
 * // host = "api-" + "blue" + "." + "us-west-2" + ".example.com"
 * // https://api-blue.us-west-2.example.com
 * }</pre>
 *
 * <p>Path placeholders are not restricted to an entire path segment. A value can complete part of a
 * segment or contain {@code '/'} and span multiple segments:
 *
 * <pre>{@code
 * SmithyUriTemplate pathTemplate = SmithyUriTemplate.builder()
 *         .scheme("https")
 *         .addHostLiteral("example.com")
 *         .addPathLiteral("/v")
 *         .addPathPlaceholder()
 *         .addPathLiteral("/items/item-")
 *         .addPathPlaceholder()
 *         .build();
 *
 * SmithyUri uri = pathTemplate.expand("2", "42/metadata");
 * // path = "/v" + "2" + "/items/item-" + "42/metadata"
 * // https://example.com/v2/items/item-42/metadata
 * }</pre>
 *
 * <p>Values are treated as raw URI component text and are validated, not automatically encoded.
 *
 * <p>Templates are immutable and safe to share.
 */
public final class SmithyUriTemplate {

    private static final String[] NO_LITERALS = new String[0];

    private final String scheme;
    private final int port;
    private final String query;

    // Alternating literals and placeholders: hostLiterals[0] + value + hostLiterals[1] + value + ...
    // so there is always exactly one more literal than placeholder.
    private final String[] hostLiterals;
    private final String[] pathLiterals;
    private final boolean[] completePathLiterals;
    private final int hostPlaceholders;
    private final int pathPlaceholders;
    private final int hostLiteralLength;
    private final int pathLiteralLength;

    private SmithyUriTemplate(Builder builder) {
        this.scheme = builder.scheme;
        this.port = builder.port;
        this.query = builder.query;
        this.hostLiterals = builder.host.literals();
        this.pathLiterals = builder.path.literals();
        this.completePathLiterals = new boolean[pathLiterals.length];

        for (int i = 0; i < pathLiterals.length; i++) {
            completePathLiterals[i] = isCompletePathLiteral(pathLiterals[i]);
        }

        this.hostPlaceholders = builder.host.placeholders;
        this.pathPlaceholders = builder.path.placeholders;
        this.hostLiteralLength = builder.host.literalLength();
        this.pathLiteralLength = builder.path.literalLength();
    }

    /**
     * Starts building a template.
     *
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The number of values {@link #expand} requires, host placeholders first then path placeholders.
     *
     * @return the placeholder count.
     */
    public int placeholderCount() {
        return hostPlaceholders + pathPlaceholders;
    }

    /**
     * Expands a template with one placeholder.
     *
     * @param v0 the value.
     * @return the URI.
     * @throws IllegalArgumentException if the value is not legal for the component it fills.
     */
    public SmithyUri expand(String v0) {
        return expandFixed(1, v0, null, null, null);
    }

    /**
     * Expands a template with two placeholders.
     *
     * @param v0 first value.
     * @param v1 second value.
     * @return the URI.
     * @throws IllegalArgumentException if a value is not legal for the component it fills.
     */
    public SmithyUri expand(String v0, String v1) {
        return expandFixed(2, v0, v1, null, null);
    }

    /**
     * Expands a template with three placeholders.
     *
     * @param v0 first value.
     * @param v1 second value.
     * @param v2 third value.
     * @return the URI.
     * @throws IllegalArgumentException if a value is not legal for the component it fills.
     */
    public SmithyUri expand(String v0, String v1, String v2) {
        return expandFixed(3, v0, v1, v2, null);
    }

    /**
     * Expands a template with four placeholders.
     *
     * @param v0 first value.
     * @param v1 second value.
     * @param v2 third value.
     * @param v3 fourth value.
     * @return the URI.
     * @throws IllegalArgumentException if a value is not legal for the component it fills.
     */
    public SmithyUri expand(String v0, String v1, String v2, String v3) {
        return expandFixed(4, v0, v1, v2, v3);
    }

    /**
     * Expands a template.
     *
     * @param values one value per placeholder, host placeholders first then path placeholders.
     * @return the URI.
     * @throws IllegalArgumentException if a value is not legal for the component it fills, or if the
     *         number of values does not match {@link #placeholderCount()}.
     */
    public SmithyUri expand(String[] values) {
        if (values.length != placeholderCount()) {
            throw new IllegalArgumentException("Expected " + placeholderCount() + " values but got " + values.length);
        }
        return expand(values, 0);
    }

    private SmithyUri expand(Object[] values, int from) {
        int hostLength = expandedLength(values, from, hostPlaceholders, hostLiteralLength);
        int pathLength = expandedLength(
                values,
                from + hostPlaceholders,
                pathPlaceholders,
                pathLiteralLength);
        return expand(values, from, new char[Math.max(hostLength, pathLength)], hostLength, pathLength);
    }

    /**
     * Expands values already staged in internal evaluator storage.
     *
     * <p><strong>Internal use only.</strong> This method exists so the rules engine can reuse its
     * evaluator-owned character buffer across module boundaries.
     *
     * @param values array holding the values.
     * @param from index of the first value.
     * @param acquireBuffer returns scratch storage of at least the requested size.
     * @return the URI.
     * @throws IllegalArgumentException if a value is not legal for the component it fills.
     * @hidden
     */
    @SmithyInternalApi
    public SmithyUri expandInternal(Object[] values, int from, IntFunction<char[]> acquireBuffer) {
        int hostLength = expandedLength(values, from, hostPlaceholders, hostLiteralLength);
        int pathLength = expandedLength(
                values,
                from + hostPlaceholders,
                pathPlaceholders,
                pathLiteralLength);
        int requiredSize = Math.max(hostLength, pathLength);
        char[] buffer = acquireBuffer.apply(requiredSize);
        if (buffer.length < requiredSize) {
            throw new IllegalArgumentException("Scratch buffer is smaller than requested");
        }
        return expand(values, from, buffer, hostLength, pathLength);
    }

    private SmithyUri expandFixed(int count, String v0, String v1, String v2, String v3) {
        if (count != placeholderCount()) {
            throw new IllegalArgumentException(
                    "Expected " + placeholderCount() + " values but got " + count);
        }

        int hostLength = expandedFixedLength(0, hostPlaceholders, hostLiteralLength, v0, v1, v2, v3);
        int pathLength = expandedFixedLength(
                hostPlaceholders,
                pathPlaceholders,
                pathLiteralLength,
                v0,
                v1,
                v2,
                v3);
        char[] scratch = new char[Math.max(hostLength, pathLength)];
        if (hostLength == 0) {
            throw new IllegalArgumentException("Host must not be empty");
        }

        String host = normalizeHost(assembleFixedHost(scratch, hostLength, v0, v1, v2, v3));
        String path = assembleFixedPath(scratch, pathLength, v0, v1, v2, v3);
        return new SmithyUri(scheme, null, host, port, path, query);
    }

    private SmithyUri expand(Object[] values, int from, char[] scratch, int hostLength, int pathLength) {
        if (hostLength == 0) {
            throw new IllegalArgumentException("Host must not be empty");
        }

        String host = normalizeHost(assembleHost(scratch, values, from, hostLength));
        String path = assemblePath(scratch, values, from + hostPlaceholders, pathLength);
        return new SmithyUri(scheme, null, host, port, path, query);
    }

    private static int expandedLength(
            Object[] values,
            int from,
            int count,
            int literalLength
    ) {
        int total = literalLength;
        for (int i = 0; i < count; i++) {
            total += ((String) values[from + i]).length();
        }
        return total;
    }

    private static int expandedFixedLength(
            int from,
            int count,
            int literalLength,
            String v0,
            String v1,
            String v2,
            String v3
    ) {
        int total = literalLength;
        for (int i = 0; i < count; i++) {
            total += fixedValue(from + i, v0, v1, v2, v3).length();
        }
        return total;
    }

    private static String fixedValue(int index, String v0, String v1, String v2, String v3) {
        return switch (index) {
            case 0 -> v0;
            case 1 -> v1;
            case 2 -> v2;
            case 3 -> v3;
            default -> throw new AssertionError("Invalid fixed value index: " + index);
        };
    }

    private String assembleHost(char[] buf, Object[] values, int from, int total) {
        if (hostPlaceholders == 0) {
            return hostLiterals.length == 0 ? "" : hostLiterals[0];
        }
        int pos = 0;
        for (int i = 0; i < hostPlaceholders; i++) {
            pos = append(buf, pos, hostLiterals[i]);
            pos = appendHostValue(buf, pos, total, (String) values[from + i]);
        }
        append(buf, pos, hostLiterals[hostPlaceholders]);
        return new String(buf, 0, total);
    }

    private String assembleFixedHost(char[] buf, int total, String v0, String v1, String v2, String v3) {
        if (hostPlaceholders == 0) {
            return hostLiterals.length == 0 ? "" : hostLiterals[0];
        }
        int pos = 0;
        for (int i = 0; i < hostPlaceholders; i++) {
            pos = append(buf, pos, hostLiterals[i]);
            pos = appendHostValue(buf, pos, total, fixedValue(i, v0, v1, v2, v3));
        }
        append(buf, pos, hostLiterals[hostPlaceholders]);
        return new String(buf, 0, total);
    }

    private String assemblePath(char[] buf, Object[] values, int from, int total) {
        if (pathPlaceholders == 0) {
            return pathLiterals.length == 0 ? "" : pathLiterals[0];
        }
        int pos = 0;
        int percentRemaining = 0;
        for (int i = 0; i < pathPlaceholders; i++) {
            String literal = pathLiterals[i];
            if (percentRemaining == 0 && completePathLiterals[i]) {
                pos = append(buf, pos, literal);
            } else {
                for (int j = 0; j < literal.length(); j++) {
                    char c = literal.charAt(j);
                    percentRemaining = checkPathChar(c, percentRemaining);
                    buf[pos++] = c;
                }
            }

            String value = (String) values[from + i];
            for (int j = 0; j < value.length(); j++) {
                char c = value.charAt(j);
                percentRemaining = checkPathChar(c, percentRemaining);
                buf[pos++] = c;
            }
        }

        String last = pathLiterals[pathPlaceholders];
        if (percentRemaining == 0 && completePathLiterals[pathPlaceholders]) {
            append(buf, pos, last);
        } else {
            for (int i = 0; i < last.length(); i++) {
                char c = last.charAt(i);
                percentRemaining = checkPathChar(c, percentRemaining);
                buf[pos++] = c;
            }
        }
        if (percentRemaining != 0) {
            throw new IllegalArgumentException("Incomplete percent-encoding in path");
        }
        return new String(buf, 0, total);
    }

    private String assembleFixedPath(char[] buf, int total, String v0, String v1, String v2, String v3) {
        if (pathPlaceholders == 0) {
            return pathLiterals.length == 0 ? "" : pathLiterals[0];
        }
        int pos = 0;
        int percentRemaining = 0;
        for (int i = 0; i < pathPlaceholders; i++) {
            String literal = pathLiterals[i];
            if (percentRemaining == 0 && completePathLiterals[i]) {
                pos = append(buf, pos, literal);
            } else {
                for (int j = 0; j < literal.length(); j++) {
                    char c = literal.charAt(j);
                    percentRemaining = checkPathChar(c, percentRemaining);
                    buf[pos++] = c;
                }
            }

            String value = fixedValue(hostPlaceholders + i, v0, v1, v2, v3);
            for (int j = 0; j < value.length(); j++) {
                char c = value.charAt(j);
                percentRemaining = checkPathChar(c, percentRemaining);
                buf[pos++] = c;
            }
        }

        String last = pathLiterals[pathPlaceholders];
        if (percentRemaining == 0 && completePathLiterals[pathPlaceholders]) {
            append(buf, pos, last);
        } else {
            for (int i = 0; i < last.length(); i++) {
                char c = last.charAt(i);
                percentRemaining = checkPathChar(c, percentRemaining);
                buf[pos++] = c;
            }
        }
        if (percentRemaining != 0) {
            throw new IllegalArgumentException("Incomplete percent-encoding in path");
        }
        return new String(buf, 0, total);
    }

    private static int append(char[] buf, int pos, String s) {
        int len = s.length();
        if (len != 0) {
            s.getChars(0, len, buf, pos);
        }
        return pos + len;
    }

    private static int appendHostValue(char[] buf, int pos, int total, String value) {
        int start = 0;
        int end = value.length();
        if (pos == 0 && end != 0 && value.charAt(0) == '[') {
            buf[pos++] = '[';
            start = 1;
        }
        boolean trailingBracket = pos + end - start == total && end > start && value.charAt(end - 1) == ']';
        if (trailingBracket) {
            end--;
        }
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            SmithyUri.validateHostCharacter(c, value);
            buf[pos++] = c;
        }
        if (trailingBracket) {
            buf[pos++] = ']';
        }
        return pos;
    }

    private static String normalizeHost(String host) {
        String normalized = SmithyUri.stripBrackets(host);
        if (normalized == host
                && (host.charAt(0) == '[' || host.charAt(host.length() - 1) == ']')) {
            throw new IllegalArgumentException("Invalid brackets in host: " + host);
        }
        return normalized;
    }

    private static int checkPathChar(char c, int percentRemaining) {
        if (percentRemaining != 0) {
            if (!SmithyUri.isHex(c)) {
                throw new IllegalArgumentException("Invalid percent-encoding in path");
            }
            return percentRemaining - 1;
        }

        if (!SmithyUri.isValidPathCharacter(c)) {
            throw new IllegalArgumentException("Invalid character in path: '" + c + "'");
        }

        return c == '%' ? 2 : 0;
    }

    private static boolean isCompletePathLiteral(String literal) {
        int lastPercent = literal.lastIndexOf('%');
        return lastPercent < 0 || lastPercent + 2 < literal.length();
    }

    // Mirrors SmithyUri.validateHost minus its whole-host emptiness rule, which a single value cannot
    // decide: an empty value is fine as long as the literals or another value make the host non-empty.
    private static void checkHostValue(String value) {
        for (int i = 0; i < value.length(); i++) {
            SmithyUri.validateHostCharacter(value.charAt(i), value);
        }
    }

    /**
     * Builds a {@link SmithyUriTemplate}, validating everything constant as it goes.
     *
     * <p>Call {@link #addHostLiteral(String)} and {@link #addPathLiteral(String)} to append constant text, and
     * {@link #addHostPlaceholder()} or {@link #addPathPlaceholder()} wherever an expansion value belongs. After
     * {@link #build()}, pass one value for each placeholder to {@link SmithyUriTemplate#expand(String[])}.
     */
    public static final class Builder {

        private String scheme;
        private int port = -1;
        private String query;
        private final Part host = new HostPart();
        private final Part path = new PathPart();

        private Builder() {}

        /**
         * Sets the scheme, validated now and never again.
         *
         * @param scheme the scheme, which must be lowercase.
         * @return this builder.
         */
        public Builder scheme(String scheme) {
            if (scheme != null) {
                SmithyUri.validateScheme(scheme);
            }
            this.scheme = scheme;
            return this;
        }

        /**
         * Sets the port, validated now and never again.
         *
         * @param port the port, or -1 for none.
         * @return this builder.
         */
        public Builder port(int port) {
            SmithyUri.validatePort(port);
            this.port = port;
            return this;
        }

        /**
         * Appends a constant run to the host, validated now.
         *
         * @param literal the text.
         * @return this builder.
         */
        public Builder addHostLiteral(String literal) {
            host.addLiteral(literal);
            return this;
        }

        /**
         * Appends a placeholder to the host. Its value is checked against the host character class when
         * the template is expanded. The value is concatenated directly with adjacent host literals; no
         * dot or other label separator is inserted automatically.
         *
         * @return this builder.
         */
        public Builder addHostPlaceholder() {
            host.addPlaceholder();
            return this;
        }

        /**
         * Appends a constant run to the path, validated now.
         *
         * @param literal the text.
         * @return this builder.
         */
        public Builder addPathLiteral(String literal) {
            path.addLiteral(literal);
            return this;
        }

        /**
         * Appends a placeholder to the path. Its value is checked against the path character class when
         * the template is expanded. A placeholder can represent part of a segment, an entire segment, or
         * multiple segments because {@code '/'} is permitted exactly as it is when validating the whole
         * path.
         *
         * @return this builder.
         */
        public Builder addPathPlaceholder() {
            path.addPlaceholder();
            return this;
        }

        /**
         * Sets a constant query string, validated now.
         *
         * @param query the query, or null.
         * @return this builder.
         */
        public Builder query(String query) {
            if (query != null) {
                SmithyUri.validateQuery(query);
            }
            this.query = query;
            return this;
        }

        /**
         * Builds the template.
         *
         * @return the template.
         */
        public SmithyUriTemplate build() {
            path.validateCompleteIfConstant();
            return new SmithyUriTemplate(this);
        }
    }

    /** Accumulates the alternating literals and placeholders of one component. */
    private abstract static sealed class Part {
        private final List<String> literals = new ArrayList<>();
        private final StringBuilder pending = new StringBuilder();
        private int placeholders;
        private int literalLength;

        final void addLiteral(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            validateLiteral(text);
            pending.append(text);
            literalLength += text.length();
        }

        final void addPlaceholder() {
            literals.add(pending.toString());
            pending.setLength(0);
            placeholders++;
        }

        final String[] literals() {
            if (placeholders == 0 && pending.isEmpty()) {
                return NO_LITERALS;
            }
            String[] result = literals.toArray(new String[placeholders + 1]);
            result[placeholders] = pending.toString();
            return result;
        }

        final int literalLength() {
            return literalLength;
        }

        final boolean hasNoPlaceholders() {
            return placeholders == 0;
        }

        final String pendingLiteral() {
            return pending.toString();
        }

        abstract void validateLiteral(String text);

        void validateCompleteIfConstant() {}
    }

    private static final class HostPart extends Part {
        @Override
        void validateLiteral(String text) {
            // The whole-host emptiness rule is handled by the template, not per literal.
            checkHostValue(text);
        }
    }

    private static final class PathPart extends Part {
        @Override
        void validateLiteral(String text) {
            // Validate every rule that can be decided within this literal. A trailing partial percent
            // escape is completed (or rejected) when the adjacent value is known during expansion.
            int length = text.length();
            for (int i = 0; i < length; i++) {
                char c = text.charAt(i);
                if (!SmithyUri.isValidPathCharacter(c)) {
                    throw new IllegalArgumentException("Invalid character in path: '" + c + "'");
                } else if (c == '%') {
                    if (i + 1 < length && !SmithyUri.isHex(text.charAt(i + 1))) {
                        throw new IllegalArgumentException("Invalid percent-encoding in path at index " + i);
                    } else if (i + 2 < length && !SmithyUri.isHex(text.charAt(i + 2))) {
                        throw new IllegalArgumentException("Invalid percent-encoding in path at index " + i);
                    }
                }
            }
        }

        @Override
        void validateCompleteIfConstant() {
            if (hasNoPlaceholders()) {
                SmithyUri.validatePath(pendingLiteral());
            }
        }
    }
}
