/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonDocuments;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * High-performance JSON deserializer that parses directly from a byte array.
 *
 * <p>Implements strict RFC 8259 compliance: validates all tokens, rejects
 * malformed input, enforces depth limits.
 */
final class SmithyJsonDeserializer implements ShapeDeserializer {

    private static final int MAX_DEPTH = 1000;

    private final byte[] buf;
    private int pos;
    private final int end;
    private final JsonSettings settings;
    private final boolean useJsonName;
    private int depth;
    private int generatedFieldStart;
    private int generatedFieldEnd;
    private String generatedFieldName;

    // Mutable result fields, avoids allocating arrays on every parse call.
    // Safe because the deserializer is single-threaded (one instance per operation).
    long parsedLong;
    int parsedEndPos;
    double parsedDouble;
    String parsedString;

    // Short-string dedup cache. Repeated JSON keys/values (common in collections) are decoded
    // once and shared. The packed bytes of a string <= 8 bytes form an exact identity key (every
    // content byte on the no-escape fast path is >= 0x20, so leading bytes are non-zero and length
    // is encoded implicitly), so a hit returns the shared String with no byte comparison.
    //
    // The arrays are reused across documents via a striped pool (allocating ~4 KB per parse
    // measurably regresses small payloads). Reuse without clearing is safe: the packed key is the
    // exact bytes, so a stale entry from a prior document is still byte-identical to any new match.
    private static final int STR_CACHE_SIZE = 256; // power of two
    private static final int STR_CACHE_MASK = STR_CACHE_SIZE - 1;

    /** Reusable dedup arrays, pooled per the pattern in {@link SmithyJsonSerializer}. */
    static final class StringCache {
        final long[] keys = new long[STR_CACHE_SIZE];
        final String[] vals = new String[STR_CACHE_SIZE];
    }

    // Striped cache pool (mirrors SmithyJsonSerializer's serializer pool). Shared by platform and
    // virtual threads alike — see acquireCache for why this is safe and memory-bounded under high
    // virtual-thread concurrency.
    private static final int CACHE_POOL_SLOTS;
    private static final int CACHE_POOL_MASK;
    private static final AtomicReferenceArray<StringCache> CACHE_POOL;
    private static final int CACHE_MAX_PROBE = 3;

    static {
        int raw = Runtime.getRuntime().availableProcessors() * 4;
        CACHE_POOL_SLOTS = Integer.highestOneBit(raw - 1) << 1;
        CACHE_POOL_MASK = CACHE_POOL_SLOTS - 1;
        CACHE_POOL = new AtomicReferenceArray<>(CACHE_POOL_SLOTS);
    }

    private long[] strCacheKeys; // null until the first short string is decoded
    private String[] strCacheVals;
    private StringCache pooledCache; // non-null when the arrays came from the pool

    SmithyJsonDeserializer(byte[] buf, int pos, int end, JsonSettings settings) {
        this.buf = buf;
        this.pos = pos;
        this.end = end;
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
        this.depth = 0;
        // Position at the first non-whitespace token
        this.pos = JsonReadUtils.skipWhitespace(buf, pos, end);
    }

    @Override
    public void close() {
        releaseCache();
        // Verify no trailing non-whitespace content
        int p = JsonReadUtils.skipWhitespace(buf, pos, end);
        if (p < end) {
            throw new SerializationException(
                    "Unexpected JSON content: " + JsonReadUtils.describePos(buf, p, end));
        }
    }

    private static int cachePoolProbe() {
        long id = Thread.currentThread().threadId();
        return (int) (id ^ (id >>> 16)) & CACHE_POOL_MASK;
    }

    /** Returns the pooled cache for reuse by a later parse on this thread group. */
    private void releaseCache() {
        StringCache cache = pooledCache;
        if (cache == null) {
            return;
        }
        pooledCache = null;
        strCacheKeys = null;
        strCacheVals = null;
        int base = cachePoolProbe();
        for (int i = 0; i < CACHE_MAX_PROBE; i++) {
            int idx = (base + i) & CACHE_POOL_MASK;
            if (CACHE_POOL.getPlain(idx) == null
                    && CACHE_POOL.compareAndExchangeRelease(idx, null, cache) == null) {
                return;
            }
        }
        // Pool full — let GC collect.
    }

    /**
     * Decodes an unescaped string, deduplicating short (&lt;= 8 byte) strings through a
     * per-document cache. The packed bytes form an exact identity (see field docs), so a
     * cache hit returns a shared, immutable String with no byte comparison. On a collision
     * the existing entry is overwritten (bounded memory), and on a miss the freshly decoded
     * String is cached. Strings longer than 8 bytes bypass the cache.
     */
    String decodeUtf8Cached(byte[] buf, int start, int len) {
        if (len == 0) {
            return "";
        }
        if (len > 8) {
            return new String(buf, start, len, StandardCharsets.UTF_8);
        }
        // Pack bytes into a long. Every content byte is >= 0x20 here (the no-escape fast
        // path rejects control bytes), so leading bytes are non-zero and length is encoded
        // implicitly — distinct (bytes,length) pairs never collide on the packed key.
        long key = 0;
        for (int i = 0; i < len; i++) {
            key = (key << 8) | (buf[start + i] & 0xFFL);
        }
        long[] keys = strCacheKeys;
        String[] vals = strCacheVals;
        if (keys == null) {
            StringCache cache = acquireCache();
            keys = strCacheKeys = cache.keys;
            vals = strCacheVals = cache.vals;
        }
        // Fibonacci hash: mix so short keys that differ only in low bits spread out.
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 48) & STR_CACHE_MASK;
        if (keys[slot] == key) {
            return vals[slot];
        }
        String s = new String(buf, start, len, StandardCharsets.UTF_8);
        keys[slot] = key;
        vals[slot] = s;
        return s;
    }

    /**
     * Decodes a scanner-proven ASCII string through the compact Latin-1 constructor.
     *
     * <p>This mirrors {@link #decodeUtf8Cached(byte[], int, int)} but avoids the UTF-8 decoder
     * after the string scanner has already established that every content byte is ASCII.
     */
    String decodeAsciiCached(byte[] buf, int start, int len) {
        if (len == 0) {
            return "";
        }
        if (len > 8) {
            return new String(buf, start, len, StandardCharsets.ISO_8859_1);
        }
        long key = 0;
        for (int i = 0; i < len; i++) {
            key = (key << 8) | (buf[start + i] & 0xFFL);
        }
        long[] keys = strCacheKeys;
        String[] vals = strCacheVals;
        if (keys == null) {
            StringCache cache = acquireCache();
            keys = strCacheKeys = cache.keys;
            vals = strCacheVals = cache.vals;
        }
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 48) & STR_CACHE_MASK;
        if (keys[slot] == key) {
            return vals[slot];
        }
        String s = new String(buf, start, len, StandardCharsets.ISO_8859_1);
        keys[slot] = key;
        vals[slot] = s;
        return s;
    }

    /**
     * Acquires a dedup cache from the striped pool (or allocates a fresh one). Mirrors
     * {@link SmithyJsonSerializer#acquire}, including its virtual-thread handling: the
     * shared pool serves platform and virtual threads alike. The CAS to null gives the
     * caller exclusive ownership until {@link #releaseCache} (safe across carrier remounts
     * — deserialization never blocks), and the in-flight count tracks concurrent parses
     * (&approx; carrier count), not the virtual-thread count, so memory stays bounded. Entries
     * are reused as-is — see field docs for why reusing a populated cache is correct.
     */
    private StringCache acquireCache() {
        int base = cachePoolProbe();
        for (int i = 0; i < CACHE_MAX_PROBE; i++) {
            int idx = (base + i) & CACHE_POOL_MASK;
            StringCache c = CACHE_POOL.getPlain(idx);
            if (c != null && CACHE_POOL.compareAndExchangeAcquire(idx, c, null) == c) {
                pooledCache = c;
                return c;
            }
        }
        StringCache c = new StringCache();
        pooledCache = c;
        return c;
    }

    @Override
    public boolean readBoolean(Schema schema) {
        skipWhitespace();
        return readBooleanValue();
    }

    boolean generatedReadBoolean() {
        return readBooleanValue();
    }

    private boolean readBooleanValue() {
        if (pos + 4 <= end && buf[pos] == 't') {
            if (buf[pos + 1] == 'r' && buf[pos + 2] == 'u' && buf[pos + 3] == 'e') {
                pos += 4;
                return true;
            }
            throw new SerializationException("Invalid token: expected 'true'");
        }
        if (pos + 5 <= end && buf[pos] == 'f') {
            if (buf[pos + 1] == 'a' && buf[pos + 2] == 'l' && buf[pos + 3] == 's' && buf[pos + 4] == 'e') {
                pos += 5;
                return false;
            }
            throw new SerializationException("Invalid token: expected 'false'");
        }
        throw new SerializationException(
                "Expected boolean, found: " + JsonReadUtils.describePos(buf, pos, end));
    }

    @Override
    public byte readByte(Schema schema) {
        skipWhitespace();
        return readByteValue();
    }

    byte generatedReadByte() {
        return readByteValue();
    }

    private byte readByteValue() {
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        if (parsedLong < Byte.MIN_VALUE || parsedLong > Byte.MAX_VALUE) {
            throw new SerializationException("Value out of byte range: " + parsedLong);
        }
        return (byte) parsedLong;
    }

    @Override
    public short readShort(Schema schema) {
        skipWhitespace();
        return readShortValue();
    }

    short generatedReadShort() {
        return readShortValue();
    }

    private short readShortValue() {
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        if (parsedLong < Short.MIN_VALUE || parsedLong > Short.MAX_VALUE) {
            throw new SerializationException("Value out of short range: " + parsedLong);
        }
        return (short) parsedLong;
    }

    @Override
    public int readInteger(Schema schema) {
        skipWhitespace();
        return readIntegerValue();
    }

    int generatedReadInteger() {
        return readIntegerValue();
    }

    private int readIntegerValue() {
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        if (parsedLong < Integer.MIN_VALUE || parsedLong > Integer.MAX_VALUE) {
            throw new SerializationException("Value out of integer range: " + parsedLong);
        }
        return (int) parsedLong;
    }

    @Override
    public long readLong(Schema schema) {
        skipWhitespace();
        return readLongValue();
    }

    long generatedReadLong() {
        return readLongValue();
    }

    private long readLongValue() {
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        return parsedLong;
    }

    @Override
    public float readFloat(Schema schema) {
        skipWhitespace();
        return readFloatValue();
    }

    float generatedReadFloat() {
        return readFloatValue();
    }

    private float readFloatValue() {
        if (pos < end && buf[pos] == '"') {
            String s = readStringValue();
            return switch (s) {
                case "NaN" -> Float.NaN;
                case "Infinity" -> Float.POSITIVE_INFINITY;
                case "-Infinity" -> Float.NEGATIVE_INFINITY;
                default -> throw new SerializationException("Expected float, found string: \"" + s + "\"");
            };
        }
        JsonReadUtils.parseDouble(buf, pos, end, this);
        pos = parsedEndPos;
        return (float) parsedDouble;
    }

    @Override
    public double readDouble(Schema schema) {
        skipWhitespace();
        return readDoubleValue();
    }

    double generatedReadDouble() {
        if (pos < end && buf[pos] != '"' && JsonReadUtils.tryParseIntegerDouble(buf, pos, end, this)) {
            pos = parsedEndPos;
            return parsedDouble;
        }
        return readDoubleValue();
    }

    private double readDoubleValue() {
        if (pos < end && buf[pos] == '"') {
            String s = readStringValue();
            return switch (s) {
                case "NaN" -> Double.NaN;
                case "Infinity" -> Double.POSITIVE_INFINITY;
                case "-Infinity" -> Double.NEGATIVE_INFINITY;
                default -> throw new SerializationException("Expected double, found string: \"" + s + "\"");
            };
        }
        JsonReadUtils.parseDouble(buf, pos, end, this);
        pos = parsedEndPos;
        return parsedDouble;
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        skipWhitespace();
        if (settings.useStringForArbitraryPrecision()) {
            if (pos >= end || buf[pos] != '"') {
                throw new SerializationException(
                        "Expected string for BigInteger, found: " + JsonReadUtils.describePos(buf, pos, end));
            }
            String s = readStringValue();
            try {
                return new BigInteger(s);
            } catch (NumberFormatException e) {
                throw new SerializationException("Invalid BigInteger value: " + s, e);
            }
        }
        if (pos >= end || (buf[pos] != '-' && (buf[pos] < '0' || buf[pos] > '9'))) {
            throw new SerializationException(
                    "Expected number for BigInteger, found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int start = pos;
        pos = JsonReadUtils.findNumberEnd(buf, pos, end);
        String numStr = new String(buf, start, pos - start, StandardCharsets.US_ASCII);
        try {
            return new BigInteger(numStr);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid BigInteger value: " + numStr, e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        skipWhitespace();
        if (settings.useStringForArbitraryPrecision()) {
            if (pos >= end || buf[pos] != '"') {
                throw new SerializationException(
                        "Expected string for BigDecimal, found: " + JsonReadUtils.describePos(buf, pos, end));
            }
            String s = readStringValue();
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                throw new SerializationException("Invalid BigDecimal value: " + s, e);
            }
        }
        if (pos >= end || (buf[pos] != '-' && (buf[pos] < '0' || buf[pos] > '9'))) {
            throw new SerializationException(
                    "Expected number for BigDecimal, found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int start = pos;
        pos = JsonReadUtils.findNumberEnd(buf, pos, end);
        String numStr = new String(buf, start, pos - start, StandardCharsets.US_ASCII);
        try {
            return new BigDecimal(numStr);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid BigDecimal value: " + numStr, e);
        }
    }

    @Override
    public String readString(Schema schema) {
        skipWhitespace();
        return readStringValue();
    }

    private String readStringValue() {
        JsonReadUtils.parseString(buf, pos, end, this);
        pos = parsedEndPos;
        return parsedString;
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        skipWhitespace();
        return readBlobValue();
    }

    ByteBuffer generatedReadBlob() {
        return readBlobValue();
    }

    private ByteBuffer readBlobValue() {
        ByteBuffer decoded = JsonReadUtils.decodeBase64String(buf, pos, end, this);
        pos = parsedEndPos;
        return decoded;
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        var format = settings.timestampResolver().resolve(schema);
        skipWhitespace();
        return readTimestampValue(format);
    }

    Instant generatedReadEpochTimestamp() {
        if (pos < end && (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9'))) {
            long fastSecond = JsonReadUtils.tryParseTenDigitEpochSecond(buf, pos, end);
            if (fastSecond >= 0) {
                pos += 10;
                return instantFromEpochSecond(fastSecond);
            }
            int startPos = pos;
            JsonReadUtils.parseLong(buf, startPos, end, this);
            int endPos = parsedEndPos;
            if (endPos >= end || (buf[endPos] != '.' && buf[endPos] != 'e' && buf[endPos] != 'E')) {
                pos = endPos;
                return instantFromEpochSecond(parsedLong);
            }
            return readFractionalEpochTimestamp(startPos, endPos);
        }
        return readTimestampFallback(TimestampFormatter.Prelude.EPOCH_SECONDS);
    }

    Instant generatedReadDateTimeTimestamp() {
        if (pos < end && buf[pos] == '"') {
            Instant result = JsonReadUtils.parseIso8601(buf, pos, end, this);
            if (result != null) {
                pos = parsedEndPos;
                return result;
            }
        }
        return readTimestampFallback(TimestampFormatter.Prelude.DATE_TIME);
    }

    Instant generatedReadHttpDateTimestamp() {
        if (pos < end && buf[pos] == '"') {
            Instant result = JsonReadUtils.parseHttpDate(buf, pos, end, this);
            if (result != null) {
                pos = parsedEndPos;
                return result;
            }
        }
        return readTimestampFallback(TimestampFormatter.Prelude.HTTP_DATE);
    }

    private Instant readTimestampValue(TimestampFormatter format) {
        if (format == TimestampFormatter.Prelude.EPOCH_SECONDS) {
            return generatedReadEpochTimestamp();
        }
        if (pos < end && buf[pos] == '"') {
            // Fast path: parse ISO-8601 and HTTP-date directly from bytes,
            // bypassing String allocation and DateTimeFormatter.
            if (format == TimestampFormatter.Prelude.DATE_TIME) {
                Instant result = JsonReadUtils.parseIso8601(buf, pos, end, this);
                if (result != null) {
                    pos = parsedEndPos;
                    return result;
                }
            } else if (format == TimestampFormatter.Prelude.HTTP_DATE) {
                Instant result = JsonReadUtils.parseHttpDate(buf, pos, end, this);
                if (result != null) {
                    pos = parsedEndPos;
                    return result;
                }
            }
        }
        return readTimestampFallback(format);
    }

    private Instant readFractionalEpochTimestamp(int startPos, int integerEnd) {
        if (buf[integerEnd] == '.') {
            // Parse with full nanosecond precision instead of going through double,
            // which truncates to roughly 15 significant digits.
            int fracPos = integerEnd + 1;
            int fracStart = fracPos;
            while (fracPos < end && buf[fracPos] >= '0' && buf[fracPos] <= '9') {
                fracPos++;
            }
            int fracLen = fracPos - fracStart;
            boolean hasExponent = fracPos < end && (buf[fracPos] == 'e' || buf[fracPos] == 'E');
            if (fracLen > 0 && !hasExponent) {
                int nano = 0;
                for (int i = 0; i < 9; i++) {
                    nano *= 10;
                    if (i < fracLen) {
                        nano += buf[fracStart + i] - '0';
                    }
                }
                pos = fracPos;
                long epochSecond = parsedLong;
                if (buf[startPos] == '-' && nano > 0) {
                    epochSecond -= 1;
                    nano = 1_000_000_000 - nano;
                }
                try {
                    return Instant.ofEpochSecond(epochSecond, nano);
                } catch (DateTimeException e) {
                    throw new SerializationException("Epoch seconds out of range: " + parsedLong, e);
                }
            }
        }
        pos = startPos;
        JsonReadUtils.parseDouble(buf, startPos, end, this);
        pos = parsedEndPos;
        return TimestampFormatter.Prelude.EPOCH_SECONDS.readFromNumber(parsedDouble);
    }

    private Instant instantFromEpochSecond(long epochSecond) {
        try {
            return Instant.ofEpochSecond(epochSecond);
        } catch (DateTimeException e) {
            throw new SerializationException("Epoch seconds out of range: " + epochSecond, e);
        }
    }

    private Instant readTimestampFallback(TimestampFormatter format) {
        if (pos < end && buf[pos] == '"') {
            String s = readStringValue();
            try {
                return format.readFromString(s, true);
            } catch (DateTimeException e) {
                throw new SerializationException("Invalid timestamp: " + s, e);
            }
        }
        if (pos < end && (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9'))) {
            JsonReadUtils.parseDouble(buf, pos, end, this);
            pos = parsedEndPos;
            return format.readFromNumber(parsedDouble);
        }
        throw new SerializationException(
                "Expected a timestamp, but found " + describeCurrentToken());
    }

    private String describeCurrentToken() {
        if (pos >= end) {
            return "end of input";
        }
        return switch (buf[pos]) {
            case 't', 'f' -> "Boolean value";
            case 'n' -> "Null value";
            case '[' -> "Array value";
            case '{' -> "Object value";
            case '"' -> "String value";
            default -> JsonReadUtils.describePos(buf, pos, end);
        };
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        // Localize hot fields to registers. The JIT cannot promote instance fields across
        // virtual calls (the structMemberConsumer callback), so keeping buf/end/pos as locals
        // between callbacks eliminates ~6 memory loads/stores per field iteration.
        final byte[] localBuf = this.buf;
        final int localEnd = this.end;
        int p = JsonReadUtils.skipWhitespace(localBuf, this.pos, localEnd);

        if (p >= localEnd) {
            this.pos = p;
            return;
        }
        if (localBuf[p] != '{') {
            this.pos = p;
            throw new SerializationException(
                    "Expected '{', found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
        }
        p++;
        depth++;
        if (depth > MAX_DEPTH) {
            this.pos = p;
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);

        if (p < localEnd && localBuf[p] == '}') {
            this.pos = p + 1;
            depth--;
            return;
        }

        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(SmithyJsonSchemaExtensions.KEY);
        SmithyMemberLookup lookup = null;
        if (ext != null) {
            lookup = useJsonName ? ext.jsonNameLookup() : ext.memberNameLookup();
        }
        int expectedNext = 0;

        boolean first = true;
        while (true) {
            if (!first) {
                p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);
                if (p >= localEnd) {
                    this.pos = p;
                    throw new SerializationException("Unterminated object");
                }
                if (localBuf[p] == '}') {
                    this.pos = p + 1;
                    depth--;
                    return;
                }
                if (p >= localEnd || localBuf[p] != ',') {
                    this.pos = p;
                    throw new SerializationException(
                            "Expected ',', found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
                }
                p++;
            }
            first = false;

            p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);

            if (p >= localEnd || localBuf[p] != '"') {
                this.pos = p;
                throw new SerializationException(
                        "Expected field name, found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
            }
            p++; // skip opening quote

            // Fused speculative path: check expected field name directly at current
            // position without scanning for the closing quote byte-by-byte. If the
            // expected name matches and the byte after it is '"', we skip the scan
            // entirely. This eliminates the per-byte scan loop on the common path
            // where JSON fields arrive in schema definition order.
            Schema member = null;
            if (lookup != null && expectedNext >= 0 && expectedNext < lookup.orderedNameBytes.length) {
                byte[] expected = lookup.orderedNameBytes[expectedNext];
                int expLen = expected.length;
                if (p + expLen < localEnd
                        && localBuf[p + expLen] == '"'
                        && Arrays.equals(localBuf, p, p + expLen, expected, 0, expLen)) {
                    member = lookup.orderedSchemas[expectedNext];
                    expectedNext = member.memberIndex() + 1;
                    p += expLen + 1; // skip name + closing quote
                }
            }

            // Slow path: scan for closing quote and look up member by hash
            int nameStart = -1, nameEnd = -1;
            if (member == null) {
                nameStart = p;
                while (p < localEnd && localBuf[p] != '"') {
                    if (localBuf[p] == '\\') {
                        p++; // skip escaped char
                    }
                    p++;
                }
                if (p >= localEnd) {
                    this.pos = p;
                    throw new SerializationException("Unterminated field name");
                }
                nameEnd = p;
                p++; // skip closing quote
            }

            p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);
            if (p >= localEnd || localBuf[p] != ':') {
                this.pos = p;
                throw new SerializationException(
                        "Expected ':', found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
            }
            p++;
            p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);

            // Slow path member lookup (only when speculative check missed)
            if (member == null) {
                member = lookup != null
                        ? lookup.lookup(localBuf, nameStart, nameEnd, expectedNext)
                        : null;
                if (member != null) {
                    expectedNext = member.memberIndex() + 1;
                }
            }

            if (member != null) {
                // Check for null value
                if (p < localEnd && localBuf[p] == 'n'
                        && p + 4 <= localEnd
                        && localBuf[p + 1] == 'u'
                        && localBuf[p + 2] == 'l'
                        && localBuf[p + 3] == 'l') {
                    p += 4;
                } else {
                    // Write pos back before callback, reload after
                    this.pos = p;
                    structMemberConsumer.accept(state, member, this);
                    p = this.pos;
                }
            } else {
                // Unknown field -- validate field name bytes per RFC 8259
                // (control chars, escape sequences). This is the cold path only.
                validateSkippedString(localBuf, nameStart, nameEnd);
                this.pos = p;
                String fieldName = new String(localBuf, nameStart, nameEnd - nameStart, StandardCharsets.UTF_8);

                if (schema.type() == ShapeType.STRUCTURE) {
                    structMemberConsumer.unknownMember(state, fieldName);
                    skipValue();
                } else if (fieldName.equals("__type")) {
                    skipValue();
                } else if (settings.forbidUnknownUnionMembers()) {
                    throw new SerializationException("Unknown member " + fieldName + " encountered");
                } else {
                    structMemberConsumer.unknownMember(state, fieldName);
                    skipValue();
                }
                p = this.pos;
            }
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        final byte[] buf = this.buf;
        final int end = this.end;
        int p = JsonReadUtils.skipWhitespace(buf, this.pos, end);

        if (p >= end || buf[p] != '[') {
            this.pos = p;
            throw new SerializationException(
                    "Expected a list, but found " + describeCurrentToken());
        }
        p++;
        depth++;
        if (depth > MAX_DEPTH) {
            this.pos = p;
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        p = JsonReadUtils.skipWhitespace(buf, p, end);

        if (p < end && buf[p] == ']') {
            this.pos = p + 1;
            depth--;
            return;
        }

        this.pos = p;
        listMemberConsumer.accept(state, this);
        p = this.pos;
        while (true) {
            p = JsonReadUtils.skipWhitespace(buf, p, end);
            if (p < end && buf[p] == ']') {
                this.pos = p + 1;
                depth--;
                return;
            }
            if (p < end && buf[p] == ',') {
                p++;
                p = JsonReadUtils.skipWhitespace(buf, p, end);
                this.pos = p;
                listMemberConsumer.accept(state, this);
                p = this.pos;
            } else {
                this.pos = p;
                throw new SerializationException(
                        "Expected end of list, but found " + describeCurrentToken());
            }
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        final byte[] buf = this.buf;
        final int end = this.end;
        int p = JsonReadUtils.skipWhitespace(buf, this.pos, end);

        if (p >= end || buf[p] != '{') {
            this.pos = p;
            throw new SerializationException(
                    "Expected '{', found: " + JsonReadUtils.describePos(buf, p, end));
        }
        p++;
        depth++;
        if (depth > MAX_DEPTH) {
            this.pos = p;
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        p = JsonReadUtils.skipWhitespace(buf, p, end);

        if (p < end && buf[p] == '}') {
            this.pos = p + 1;
            depth--;
            return;
        }

        boolean first = true;
        while (true) {
            if (!first) {
                p = JsonReadUtils.skipWhitespace(buf, p, end);
                if (p < end && buf[p] == '}') {
                    this.pos = p + 1;
                    depth--;
                    return;
                }
                if (p >= end || buf[p] != ',') {
                    this.pos = p;
                    throw new SerializationException(
                            "Expected ',', found: " + JsonReadUtils.describePos(buf, p, end));
                }
                p++;
                p = JsonReadUtils.skipWhitespace(buf, p, end);
            }
            first = false;

            // Parse key -- need to write pos back for readStringValue
            this.pos = p;
            String key = readStringValue();
            p = JsonReadUtils.skipWhitespace(buf, this.pos, end);
            if (p >= end || buf[p] != ':') {
                this.pos = p;
                throw new SerializationException(
                        "Expected ':', found: " + JsonReadUtils.describePos(buf, p, end));
            }
            p++;
            p = JsonReadUtils.skipWhitespace(buf, p, end);

            this.pos = p;
            mapMemberConsumer.accept(state, key, this);
            p = this.pos;
        }
    }

    @Override
    public Document readDocument() {
        skipWhitespace();
        if (pos >= end) {
            throw new SerializationException("Expected a JSON value");
        }

        return switch (buf[pos]) {
            case 'n' -> {
                expectLiteral("null");
                yield null;
            }
            case 't' -> {
                expectLiteral("true");
                yield JsonDocuments.of(true, settings);
            }
            case 'f' -> {
                expectLiteral("false");
                yield JsonDocuments.of(false, settings);
            }
            case '"' -> JsonDocuments.of(readStringValue(), settings);
            case '[' -> {
                pos++; // skip '['
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new SerializationException("Maximum nesting depth exceeded");
                }
                List<Document> values = new ArrayList<>();
                skipWhitespace();
                if (pos < end && buf[pos] != ']') {
                    values.add(readDocument());
                    skipWhitespace();
                    while (pos < end && buf[pos] == ',') {
                        pos++;
                        values.add(readDocument());
                        skipWhitespace();
                    }
                }
                expect(']');
                depth--;
                yield JsonDocuments.of(values, settings);
            }
            case '{' -> {
                pos++; // skip '{'
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new SerializationException("Maximum nesting depth exceeded");
                }
                Map<String, Document> values = new LinkedHashMap<>();
                skipWhitespace();
                if (pos < end && buf[pos] != '}') {
                    String key = readStringValue();
                    skipWhitespace();
                    expect(':');
                    values.put(key, readDocument());
                    skipWhitespace();
                    while (pos < end && buf[pos] == ',') {
                        pos++;
                        skipWhitespace();
                        key = readStringValue();
                        skipWhitespace();
                        expect(':');
                        values.put(key, readDocument());
                        skipWhitespace();
                    }
                }
                expect('}');
                depth--;
                yield JsonDocuments.of(values, settings);
            }
            default -> {
                // Must be a number
                if (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9')) {
                    yield parseDocumentNumber();
                }
                throw new SerializationException(
                        "Unexpected token: " + JsonReadUtils.describePos(buf, pos, end));
            }
        };
    }

    /**
     * Parses a number in document context with strict RFC 8259 validation.
     * Determines the appropriate Number type (Integer, Long, BigInteger, or Double).
     */
    private Document parseDocumentNumber() {
        // Use parseDouble to strictly validate the number grammar
        JsonReadUtils.parseDouble(buf, pos, end, this);
        int newPos = parsedEndPos;

        // Check if the number has fractional/exponent parts
        boolean isFloat = false;
        for (int i = pos; i < newPos; i++) {
            if (buf[i] == '.' || buf[i] == 'e' || buf[i] == 'E') {
                isFloat = true;
                break;
            }
        }

        Number number;
        if (isFloat) {
            number = parsedDouble;
            pos = newPos;
        } else {
            int len = newPos - pos;
            int digitStart = pos;
            if (buf[pos] == '-') {
                digitStart++;
            }
            int digitLen = newPos - digitStart;
            if (digitLen <= 18) {
                long lv = NumberCodec.parseLong(buf, pos, len);
                if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                    number = (int) lv;
                } else {
                    number = lv;
                }
            } else {
                String numStr = new String(buf, pos, len, StandardCharsets.US_ASCII);
                try {
                    long lv = Long.parseLong(numStr);
                    if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                        number = (int) lv;
                    } else {
                        number = lv;
                    }
                } catch (NumberFormatException e) {
                    number = new BigInteger(numStr);
                }
            }
            pos = newPos;
        }
        return JsonDocuments.of(number, settings);
    }

    @Override
    public boolean isNull() {
        skipWhitespace();
        return pos < end && buf[pos] == 'n';
    }

    @Override
    public <T> T readNull() {
        skipWhitespace();
        expectLiteral("null");
        return null;
    }

    /**
     * Validates string bytes (between quotes) for RFC 8259 compliance without building a String.
     * Checks for unescaped control characters and valid escape sequences.
     */
    private static void validateSkippedString(byte[] buf, int start, int end) {
        int p = start;
        while (p < end) {
            byte b = buf[p];
            if (b == '\\') {
                p++;
                if (p >= end) {
                    throw new SerializationException("Unterminated escape sequence");
                }
                byte esc = buf[p];
                switch (esc) {
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                    }
                    case 'u' -> {
                        if (p + 4 >= end) {
                            throw new SerializationException("Unterminated \\u escape");
                        }
                        p += 4;
                    }
                    default -> throw new SerializationException(
                            "Invalid escape sequence: \\" + (char) esc);
                }
            } else if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character (0x" + Integer.toHexString(b & 0xFF) + ")");
            }
            p++;
        }
    }

    private void skipValue() {
        skipWhitespace();
        if (pos >= end) {
            throw new SerializationException("Unexpected end of input");
        }

        switch (buf[pos]) {
            case '"' -> skipString();
            case '{' -> skipObject();
            case '[' -> skipArray();
            case 't' -> {
                expectLiteral("true");
            }
            case 'f' -> {
                expectLiteral("false");
            }
            case 'n' -> {
                expectLiteral("null");
            }
            default -> {
                if (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9')) {
                    // Use parseDouble for strict RFC 8259 number validation
                    // (rejects leading zeros, double exponents, etc.)
                    JsonReadUtils.parseDouble(buf, pos, end, this);
                    pos = parsedEndPos;
                } else {
                    throw new SerializationException(
                            "Unexpected token: " + JsonReadUtils.describePos(buf, pos, end));
                }
            }
        }
    }

    boolean generatedBeginObject() {
        if (pos >= end || buf[pos] != '{') {
            throw new SerializationException(
                    "Expected '{', found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        pos++;
        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        skipWhitespace();
        if (pos < end && buf[pos] == '}') {
            pos++;
            depth--;
            return false;
        }
        return true;
    }

    int generatedReadFieldHash() {
        skipWhitespace();
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException(
                    "Expected field name, found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int start = ++pos;
        int hash = 0;
        while (pos < end && buf[pos] != '"') {
            byte value = buf[pos];
            if (value == '\\' || (value & 0xff) < 0x20) {
                int fieldStart = start - 1;
                JsonReadUtils.parseString(buf, fieldStart, end, this);
                String decoded = parsedString;
                generatedFieldName = decoded;
                generatedFieldStart = -1;
                generatedFieldEnd = -1;
                pos = parsedEndPos;
                skipWhitespace();
                expect(':');
                skipWhitespace();
                return decoded.hashCode();
            }
            hash = 31 * hash + (value & 0xff);
            pos++;
        }
        if (pos >= end) {
            throw new SerializationException("Unterminated field name");
        }
        generatedFieldStart = start;
        generatedFieldEnd = pos++;
        generatedFieldName = null;
        skipWhitespace();
        expect(':');
        skipWhitespace();
        return hash;
    }

    int generatedReadStringHash() {
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException(
                    "Expected string, found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int start = ++pos;
        int hash = 0;
        while (pos < end && buf[pos] != '"') {
            byte value = buf[pos];
            if (value == '\\' || value < 0 || (value & 0xff) < 0x20) {
                JsonReadUtils.parseString(buf, start - 1, end, this);
                generatedFieldName = parsedString;
                generatedFieldStart = -1;
                generatedFieldEnd = -1;
                pos = parsedEndPos;
                return generatedFieldName.hashCode();
            }
            hash = 31 * hash + value;
            pos++;
        }
        if (pos >= end) {
            throw new SerializationException("Unterminated string");
        }
        generatedFieldStart = start;
        generatedFieldEnd = pos++;
        generatedFieldName = null;
        return hash;
    }

    boolean generatedTryReadField(byte[] token) {
        int start = pos;
        int length = token.length;
        if (start > end - length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (buf[start + i] != token[i]) {
                return false;
            }
        }
        pos = JsonReadUtils.skipWhitespace(buf, start + length, end);
        return true;
    }

    boolean generatedTryReadNextField(byte[] token) {
        int start = pos;
        if (start >= end || buf[start] != ',') {
            return false;
        }
        start++;
        int length = token.length;
        if (start > end - length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (buf[start + i] != token[i]) {
                return false;
            }
        }
        pos = JsonReadUtils.skipWhitespace(buf, start + length, end);
        return true;
    }

    boolean generatedTryReadField8(long expected, long mask, int length) {
        int start = pos;
        if (start > end - length) {
            return false;
        }
        long actual;
        if (start <= end - Long.BYTES) {
            actual = JsonReadUtils.readLongLittleEndian(buf, start) & mask;
        } else {
            actual = readPackedToken(start, length);
        }
        if (actual != expected) {
            return false;
        }
        pos = JsonReadUtils.skipWhitespace(buf, start + length, end);
        return true;
    }

    boolean generatedTryReadNextField8(long expected, long mask, int length) {
        int start = pos;
        if (start >= end || buf[start] != ',') {
            return false;
        }
        start++;
        if (start > end - length) {
            return false;
        }
        long actual;
        if (start <= end - Long.BYTES) {
            actual = JsonReadUtils.readLongLittleEndian(buf, start) & mask;
        } else {
            actual = readPackedToken(start, length);
        }
        if (actual != expected) {
            return false;
        }
        pos = JsonReadUtils.skipWhitespace(buf, start + length, end);
        return true;
    }

    boolean generatedTryReadField16(long prefix, long suffix, long suffixMask, int length) {
        int start = pos;
        if (start > end - length || JsonReadUtils.readLongLittleEndian(buf, start) != prefix) {
            return false;
        }
        int suffixStart = start + Long.BYTES;
        long actualSuffix;
        if (suffixStart <= end - Long.BYTES) {
            actualSuffix = JsonReadUtils.readLongLittleEndian(buf, suffixStart) & suffixMask;
        } else {
            actualSuffix = readPackedToken(suffixStart, length - Long.BYTES);
        }
        if (actualSuffix != suffix) {
            return false;
        }
        pos = JsonReadUtils.skipWhitespace(buf, start + length, end);
        return true;
    }

    boolean generatedTryReadNextField16(long prefix, long suffix, long suffixMask, int length) {
        int start = pos;
        if (start >= end || buf[start] != ',') {
            return false;
        }
        start++;
        if (start > end - length || JsonReadUtils.readLongLittleEndian(buf, start) != prefix) {
            return false;
        }
        int suffixStart = start + Long.BYTES;
        long actualSuffix;
        if (suffixStart <= end - Long.BYTES) {
            actualSuffix = JsonReadUtils.readLongLittleEndian(buf, suffixStart) & suffixMask;
        } else {
            actualSuffix = readPackedToken(suffixStart, length - Long.BYTES);
        }
        if (actualSuffix != suffix) {
            return false;
        }
        pos = JsonReadUtils.skipWhitespace(buf, start + length, end);
        return true;
    }

    private long readPackedToken(int start, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value |= (long) (buf[start + i] & 0xFF) << (i << 3);
        }
        return value;
    }

    boolean generatedFieldEquals(byte[] expected) {
        if (generatedFieldName != null) {
            return generatedFieldName.equals(new String(expected, StandardCharsets.UTF_8));
        }
        int start = generatedFieldStart;
        int length = generatedFieldEnd - start;
        return start >= 0
                && length == expected.length
                && Arrays.equals(buf, start, generatedFieldEnd, expected, 0, expected.length);
    }

    boolean generatedStringEquals8(long expected, long mask, int length, String decoded) {
        if (generatedFieldName != null) {
            return generatedFieldName.equals(decoded);
        }
        int start = generatedFieldStart;
        if (generatedFieldEnd - start != length) {
            return false;
        }
        long actual = start <= end - Long.BYTES
                ? JsonReadUtils.readLongLittleEndian(buf, start) & mask
                : readPackedToken(start, length);
        return actual == expected;
    }

    boolean generatedStringEquals16(
            long prefix,
            long suffix,
            long suffixMask,
            int length,
            String decoded
    ) {
        if (generatedFieldName != null) {
            return generatedFieldName.equals(decoded);
        }
        int start = generatedFieldStart;
        if (generatedFieldEnd - start != length
                || JsonReadUtils.readLongLittleEndian(buf, start) != prefix) {
            return false;
        }
        int suffixStart = start + Long.BYTES;
        long actualSuffix = suffixStart <= end - Long.BYTES
                ? JsonReadUtils.readLongLittleEndian(buf, suffixStart) & suffixMask
                : readPackedToken(suffixStart, length - Long.BYTES);
        return actualSuffix == suffix;
    }

    String generatedFieldName() {
        if (generatedFieldName != null) {
            return generatedFieldName;
        }
        generatedFieldName = decodeUtf8Cached(
                buf,
                generatedFieldStart,
                generatedFieldEnd - generatedFieldStart);
        return generatedFieldName;
    }

    String generatedReadMapKey() {
        skipWhitespace();
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException(
                    "Expected map key, found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int start = ++pos;
        while (pos < end) {
            byte value = buf[pos];
            if (value == '"') {
                String key = decodeUtf8Cached(buf, start, pos - start);
                pos++;
                skipWhitespace();
                expect(':');
                skipWhitespace();
                return key;
            }
            if (value == '\\') {
                JsonReadUtils.parseString(buf, start - 1, end, this);
                String key = parsedString;
                pos = parsedEndPos;
                skipWhitespace();
                expect(':');
                skipWhitespace();
                return key;
            }
            if ((value & 0xff) < 0x20) {
                throw new SerializationException("Unescaped control character in map key");
            }
            pos++;
        }
        throw new SerializationException("Unterminated map key");
    }

    boolean generatedObjectHasNext() {
        skipWhitespace();
        if (pos < end && buf[pos] == '}') {
            pos++;
            depth--;
            return false;
        }
        expect(',');
        skipWhitespace();
        return true;
    }

    boolean generatedBeginArray() {
        if (pos >= end || buf[pos] != '[') {
            throw new SerializationException(
                    "Expected '[', found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        pos++;
        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        skipWhitespace();
        if (pos < end && buf[pos] == ']') {
            pos++;
            depth--;
            return false;
        }
        return true;
    }

    boolean generatedArrayHasNext() {
        skipWhitespace();
        if (pos < end && buf[pos] == ']') {
            pos++;
            depth--;
            return false;
        }
        expect(',');
        skipWhitespace();
        return true;
    }

    boolean generatedTryReadNull() {
        if (pos + 4 <= end
                && buf[pos] == 'n'
                && buf[pos + 1] == 'u'
                && buf[pos + 2] == 'l'
                && buf[pos + 3] == 'l') {
            pos += 4;
            return true;
        }
        return false;
    }

    String generatedReadString() {
        return readStringValue();
    }

    void generatedSkipValue() {
        skipValue();
    }

    int generatedScan() {
        int start = pos;
        skipValue();
        close();
        return pos - start;
    }

    private void skipString() {
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException(
                    "Expected '\"', found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int p = pos + 1; // skip opening '"'
        final byte[] buf = this.buf;
        final int end = this.end;
        while (p < end) {
            byte b = buf[p];
            if (b == '"') {
                pos = p + 1;
                return;
            }
            if (b == '\\') {
                p++;
                if (p >= end) {
                    break;
                }
                byte esc = buf[p];
                switch (esc) {
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                    }
                    case 'u' -> {
                        // \\uXXXX -- skip 4 hex digits
                        if (p + 4 >= end) {
                            throw new SerializationException("Unterminated \\u escape");
                        }
                        p += 4;
                    }
                    default -> throw new SerializationException(
                            "Invalid escape sequence: \\" + (char) esc);
                }
            } else if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character (0x" + Integer.toHexString(b & 0xFF) + ")");
            }
            p++;
        }
        throw new SerializationException("Unterminated string");
    }

    private void skipObject() {
        pos++; // skip '{'
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded");
        }
        skipWhitespace();

        if (pos < end && buf[pos] == '}') {
            pos++;
            depth--;
            return;
        }

        boolean first = true;
        while (true) {
            if (!first) {
                skipWhitespace();
                if (pos >= end) {
                    throw new SerializationException("Unexpected end of input in object");
                }
                if (buf[pos] == '}') {
                    pos++;
                    depth--;
                    return;
                }
                expect(',');
                skipWhitespace();
            }
            first = false;
            skipString(); // skip key
            skipWhitespace();
            expect(':');
            skipValue(); // skip value
        }
    }

    private void skipArray() {
        pos++; // skip '['
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded");
        }
        skipWhitespace();

        if (pos < end && buf[pos] == ']') {
            pos++;
            depth--;
            return;
        }

        boolean first = true;
        while (true) {
            if (!first) {
                skipWhitespace();
                if (pos >= end) {
                    throw new SerializationException("Unexpected end of input in array");
                }
                if (buf[pos] == ']') {
                    pos++;
                    depth--;
                    return;
                }
                expect(',');
                skipWhitespace();
            }
            first = false;
            skipValue();
        }
    }

    private void skipWhitespace() {
        pos = JsonReadUtils.skipWhitespace(buf, pos, end);
    }

    private void expect(char c) {
        if (pos >= end || buf[pos] != c) {
            throw new SerializationException(
                    "Expected '" + c + "', found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        pos++;
    }

    private void expectLiteral(String literal) {
        int len = literal.length();
        if (pos + len > end) {
            throw new SerializationException("Unexpected end of input, expected '" + literal + "'");
        }
        for (int i = 0; i < len; i++) {
            if (buf[pos + i] != literal.charAt(i)) {
                throw new SerializationException("Expected '" + literal + "', found: "
                        + new String(buf, pos, Math.min(len, end - pos), StandardCharsets.US_ASCII));
            }
        }
        pos += len;
    }
}
