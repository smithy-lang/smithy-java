/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;

class SigningResourcesPropertyTest {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    /** The formatter supports UTC years 0000 through 9999. */
    static final long MIN_SECOND = -62_167_219_200L;
    static final long MAX_SECOND = 253_402_300_799L;

    @Test
    void headerValueCanonicalizationMatchesReference() {
        var random = new Random(1);
        var r = new SigningResources();
        for (int i = 0; i < 50_000; i++) {
            String value = i % 4 == 0 ? randomUtf16(random, 40) : randomLatin1(random, 40);
            assertHeaderValue(r, value);
        }
    }

    static void assertHeaderValue(SigningResources r, String value) {
        r.beginOutput();
        r.putHeaderValue(value);
        byte[] got = Arrays.copyOf(r.out, r.outLength);
        byte[] expected = StringSigV4Reference.canonicalHeaderValue(value).getBytes(StandardCharsets.UTF_8);
        assertThat(got).as("value %s", escape(value)).isEqualTo(expected);
    }

    @Test
    void headerNameLowercasingMatchesReference() {
        var random = new Random(2);
        var r = new SigningResources();
        for (int i = 0; i < 50_000; i++) {
            assertHeaderName(r, randomHeaderName(random));
        }
    }

    static void assertHeaderName(SigningResources r, String name) {
        boolean asciiUpper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            asciiUpper |= c >= 'A' && c <= 'Z';
        }
        String expected = asciiUpper ? name.toLowerCase(Locale.ROOT) : name;
        r.reset();
        r.addHeaderForSigning(name, "v");
        if (SigningResources.isExcludedFromSigning(expected)) {
            assertThat(r.headerCount).as("excluded %s", escape(name)).isZero();
            return;
        }
        assertThat(r.headerCount).as("name %s", escape(name)).isEqualTo(1);
        assertThat(r.headers[0]).as("name %s", escape(name)).isEqualTo(expected);
        if (!asciiUpper) {
            assertThat(r.headers[0]).as("already-lowercase name must be kept by identity").isSameAs(name);
        }
    }

    @Test
    void hmacMatchesJcaWithPreKeyedPads() throws Exception {
        hmacProperty(new SigningResources(), new Random(3));
    }

    @Test
    void hmacMatchesJcaOnTheMacFallback() throws Exception {
        hmacProperty(withoutDigestCloning(new SigningResources()), new Random(4));
    }

    private static void hmacProperty(SigningResources r, Random random) throws Exception {
        byte[] previousKey = null;
        for (int i = 0; i < 5_000; i++) {
            byte[] key = previousKey != null && random.nextInt(3) == 0 ? previousKey : randomBytes(random, 1, 200);
            byte[] data = randomBytes(random, 0, 300);
            assertHmac(r, key, data, random.nextInt(8));
            if (random.nextInt(4) == 0) {
                assertHash(r, randomBytes(random, 0, 100), 0, 0);
            }
            previousKey = key;
        }
    }

    static void assertHmac(SigningResources r, byte[] key, byte[] data, int pad) throws Exception {
        byte[] expected = StringSigV4Reference.hmac(key, data);
        assertThat(r.hmac(key, data)).as("hmac key[%d] data[%d]", key.length, data.length).isEqualTo(expected);
        byte[] padded = new byte[data.length + pad + 3];
        System.arraycopy(data, 0, padded, pad, data.length);
        byte[] out = new byte[32];
        assertThat(r.hmac(key, padded, pad, data.length, out)).isSameAs(out);
        assertThat(out).as("hmac with offset %d", pad).isEqualTo(expected);
    }

    @Test
    void hashMatchesJcaForEveryBufferKind() throws Exception {
        var random = new Random(5);
        var r = new SigningResources();
        int[] sizes = {0, 1, 31, 32, 33, 63, 64, 65, 4095, 4096, 4097, 8191, 8192, 8193, 16_384, 16_385, 40_000};
        for (int i = 0; i < 3_000; i++) {
            byte[] data = random.nextInt(3) == 0 ? randomBytes(random, 0, 300) : randomBytes(random, sizes);
            assertHash(r, data, random.nextInt(10), random.nextInt(4));
        }
    }

    static void assertHash(SigningResources r, byte[] data, int pad, int kind) throws Exception {
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(data);
        assertThat(r.hash(data)).as("hash[%d]", data.length).isEqualTo(expected);

        byte[] padded = new byte[data.length + pad + 7];
        System.arraycopy(data, 0, padded, pad, data.length);
        byte[] out = new byte[32];
        assertThat(r.hash(padded, pad, data.length, out)).isSameAs(out);
        assertThat(out).as("hash offset %d", pad).isEqualTo(expected);

        ByteBuffer buffer = switch (kind) {
            case 0 -> ByteBuffer.wrap(padded, pad, data.length);
            case 1 -> ByteBuffer.wrap(padded, pad, data.length).slice();
            case 2 -> {
                var direct = ByteBuffer.allocateDirect(padded.length);
                direct.put(padded).position(pad).limit(pad + data.length);
                yield direct;
            }
            default -> ByteBuffer.wrap(padded, pad, data.length).asReadOnlyBuffer();
        };
        int position = buffer.position();
        byte[] viaBuffer = new byte[32];
        r.hash(buffer, viaBuffer);
        assertThat(viaBuffer).as("hash via buffer kind %d size %d", kind, data.length).isEqualTo(expected);
        assertThat(buffer.position()).as("buffer position must be untouched").isEqualTo(position);

        int split = data.length / 2;
        var first = buffer.duplicate().limit(position + split);
        var second = buffer.duplicate().position(position + split);
        var stream = DataStream.ofByteBuffers(List.of(first, second), data.length, "application/octet-stream");
        byte[] viaStream = new byte[32];
        r.hash(stream, viaStream);
        assertThat(viaStream).as("hash via stream kind %d size %d", kind, data.length).isEqualTo(expected);
        assertThat(first.position()).as("stream view position must be untouched").isEqualTo(position);
    }

    @Test
    void timestampMatchesJavaTimeOverTheFourDigitYearDomain() {
        var random = new Random(6);
        var r = new SigningResources();
        long[] fixed = {MIN_SECOND, MIN_SECOND + 1, -1, 0, 1, 86_399, 86_400, MAX_SECOND - 1, MAX_SECOND};
        for (long s : fixed) {
            assertTimestamp(r, s);
        }
        for (int i = 0; i < 100_000; i++) {
            long s = MIN_SECOND + random.nextLong(MAX_SECOND - MIN_SECOND + 1);
            if (random.nextInt(3) == 0) {
                s = Math.floorDiv(s, 86_400L) * 86_400L + random.nextInt(3) - 1;
            }
            assertTimestamp(r, s);
        }
    }

    static void assertTimestamp(SigningResources r, long second) {
        r.timestamp(second);
        String expected = STAMP.format(Instant.ofEpochSecond(second));
        assertThat(r.requestTime).as("second %d", second).isEqualTo(expected);
        assertThat(r.dateStamp).as("second %d", second).isEqualTo(expected.substring(0, 8));
        assertThat(r.cachedDays).as("second %d", second).isEqualTo(Math.floorDiv(second, 86_400L));
        String memoized = r.requestTime;
        r.timestamp(second);
        assertThat(r.requestTime).as("same second must reuse the formatted string").isSameAs(memoized);
    }

    @Test
    void sortMemoAlwaysYieldsAStableSort() {
        var random = new Random(7);
        var r = new SigningResources();
        String[] names = {"a", "b", "c", "host", "x-a", "x-b", "content-type", "x-amz-target", "range"};
        String[] previous = null;
        for (int i = 0; i < 20_000; i++) {
            int n = random.nextInt(12);
            String[] sequence;
            if (previous != null && random.nextInt(3) == 0) {
                sequence = previous;
            } else {
                sequence = new String[n];
                for (int k = 0; k < n; k++) {
                    String name = names[random.nextInt(names.length)];
                    sequence[k] = random.nextInt(8) == 0 ? new String(name) : name;
                }
            }
            String[] values = new String[sequence.length];
            for (int k = 0; k < values.length; k++) {
                values[k] = Integer.toString(random.nextInt());
            }
            assertStableSort(r, sequence, values);
            previous = sequence;
        }
    }

    static void assertStableSort(SigningResources r, String[] names, String[] values) {
        r.reset();
        for (int i = 0; i < names.length; i++) {
            r.addHeaderCanonical(names[i], values[i]);
        }
        r.sortHeadersByName();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            order.add(i);
        }
        order.sort((x, y) -> names[x].compareTo(names[y]));
        List<String> expected = new ArrayList<>();
        for (int idx : order) {
            expected.add(names[idx] + "=" + values[idx]);
        }
        List<String> got = new ArrayList<>();
        for (int i = 0; i < r.headerCount; i++) {
            got.add(r.headers[i * 2] + "=" + r.headers[i * 2 + 1]);
        }
        assertThat(got).as("sorting %s", Arrays.toString(names)).isEqualTo(expected);
    }

    static SigningResources withoutDigestCloning(SigningResources r) throws Exception {
        Field field = SigningResources.class.getDeclaredField("digestCloneSupported");
        field.setAccessible(true);
        field.setBoolean(r, false);
        return r;
    }

    static String randomLatin1(Random random, int maxLength) {
        int length = random.nextInt(maxLength + 1);
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            int k = random.nextInt(100);
            int c;
            if (k < 50) {
                c = 0x21 + random.nextInt(0x5E);
            } else if (k < 62) {
                c = ' ';
            } else if (k < 70) {
                c = '\t';
            } else if (k < 78) {
                c = '\t' + random.nextInt(4);
            } else if (k < 84) {
                c = random.nextInt(0x20);
            } else if (k < 86) {
                c = 0x7F;
            } else {
                c = 0x80 + random.nextInt(0x80);
            }
            bytes[i] = (byte) c;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /**
     * Exclude unpaired surrogates because the fallback encoder emits four bytes where UTF-8 emits {@code ?}.
     * {@code HttpHeaders} rejects values above U+00FF, so requests cannot contain these characters.
     */
    static String randomUtf16(Random random, int maxLength) {
        int length = 1 + random.nextInt(maxLength);
        var sb = new StringBuilder(length + 2);
        for (int i = 0; i < length; i++) {
            int k = random.nextInt(100);
            if (k < 40) {
                sb.append((char) (0x20 + random.nextInt(0x60)));
            } else if (k < 55) {
                sb.append((char) ('\t' + random.nextInt(5)));
            } else if (k < 65) {
                sb.append((char) (0x80 + random.nextInt(0x80)));
            } else if (k < 80) {
                sb.append((char) (0x100 + random.nextInt(0x700)));
            } else if (k < 92) {
                char c = (char) (0x800 + random.nextInt(0xF800));
                sb.append(Character.isSurrogate(c) ? 'x' : c);
            } else {
                sb.appendCodePoint(0x10000 + random.nextInt(0x100000));
            }
        }
        if (sb.chars().allMatch(c -> c < 0x100)) {
            sb.append('Ā');
        }
        return sb.toString();
    }

    static String randomHeaderName(Random random) {
        int length = random.nextInt(41);
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int k = random.nextInt(100);
            if (k < 45) {
                sb.append((char) ('a' + random.nextInt(26)));
            } else if (k < 65) {
                sb.append((char) ('A' + random.nextInt(26)));
            } else if (k < 75) {
                sb.append((char) ('0' + random.nextInt(10)));
            } else if (k < 85) {
                sb.append('-');
            } else if (k < 97) {
                sb.append((char) (0x80 + random.nextInt(0x80)));
            } else {
                sb.append('Ā');
            }
        }
        if (random.nextInt(10) == 0) {
            String[] excluded = {"authorization", "X-Amz-Date", "User-Agent", "content-length", "EXPECT"};
            return excluded[random.nextInt(excluded.length)];
        }
        return sb.toString();
    }

    static byte[] randomBytes(Random random, int minLength, int maxLength) {
        byte[] bytes = new byte[minLength + random.nextInt(maxLength - minLength + 1)];
        random.nextBytes(bytes);
        return bytes;
    }

    static byte[] randomBytes(Random random, int[] sizes) {
        byte[] bytes = new byte[sizes[random.nextInt(sizes.length)]];
        random.nextBytes(bytes);
        return bytes;
    }

    static String escape(String s) {
        var sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c >= 0x7F) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
