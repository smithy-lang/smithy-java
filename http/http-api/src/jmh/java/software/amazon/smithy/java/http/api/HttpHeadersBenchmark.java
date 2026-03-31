/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for array-backed HTTP headers.
 *
 * <p>Run with: {@code ./gradlew :http:http-api:jmh}
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class HttpHeadersBenchmark {

    // Typical HTTP response headers
    private static final String[] HEADER_NAMES = {
            "content-type",
            "content-length",
            "date",
            "server",
            "x-amzn-requestid",
            "x-amz-id-2",
            "cache-control",
            "etag",
            "transfer-encoding",
            "connection",
            "vary",
            "x-amz-cf-id"
    };

    private static final String[] HEADER_VALUES = {
            "application/json",
            "1234",
            "Mon, 30 Mar 2026 12:00:00 GMT",
            "AmazonS3",
            "abc-123-def-456",
            "some+opaque+id",
            "no-cache",
            "\"etag-value\"",
            "chunked",
            "keep-alive",
            "Accept-Encoding",
            "cf-id-value"
    };

    private static final Map<String, List<String>> HEADER_MAP;

    static {
        var builder = new java.util.LinkedHashMap<String, List<String>>();
        for (int i = 0; i < HEADER_NAMES.length; i++) {
            builder.put(HEADER_NAMES[i], List.of(HEADER_VALUES[i]));
        }
        HEADER_MAP = Map.copyOf(builder);
    }

    private ModifiableHttpHeaders populatedModifiable;
    private HttpHeaders populatedUnmodifiable;

    @Setup
    public void setup() {
        populatedModifiable = HttpHeaders.ofModifiable();
        for (int i = 0; i < HEADER_NAMES.length; i++) {
            populatedModifiable.addHeader(HEADER_NAMES[i], HEADER_VALUES[i]);
        }
        populatedUnmodifiable = HttpHeaders.of(HEADER_MAP);
    }

    // --- Creation ---

    @Benchmark
    public ModifiableHttpHeaders createAndPopulateModifiable() {
        var h = HttpHeaders.ofModifiable();
        for (int i = 0; i < HEADER_NAMES.length; i++) {
            h.addHeader(HEADER_NAMES[i], HEADER_VALUES[i]);
        }
        return h;
    }

    @Benchmark
    public HttpHeaders createUnmodifiableFromMap() {
        return HttpHeaders.of(HEADER_MAP);
    }

    // --- Read (modifiable) ---

    @Benchmark
    public void firstValueAllHeaders(Blackhole bh) {
        for (String name : HEADER_NAMES) {
            bh.consume(populatedModifiable.firstValue(name));
        }
    }

    @Benchmark
    public String firstValueSingleKnown() {
        return populatedModifiable.firstValue("content-type");
    }

    @Benchmark
    public String firstValueSingleMiss() {
        return populatedModifiable.firstValue("x-nonexistent-header");
    }

    @Benchmark
    public List<String> allValuesLookup() {
        return populatedModifiable.allValues("content-type");
    }

    @Benchmark
    public boolean hasHeaderHit() {
        return populatedModifiable.hasHeader("content-type");
    }

    @Benchmark
    public boolean hasHeaderMiss() {
        return populatedModifiable.hasHeader("x-nonexistent-header");
    }

    @Benchmark
    public void iterateHeaders(Blackhole bh) {
        populatedModifiable.forEachEntry((name, value) -> {
            bh.consume(name);
            bh.consume(value);
        });
    }

    @Benchmark
    public Map<String, List<String>> toMap() {
        return populatedModifiable.map();
    }

    // --- Read (unmodifiable) ---

    @Benchmark
    public String unmodifiableFirstValue() {
        return populatedUnmodifiable.firstValue("content-type");
    }

    @Benchmark
    public String unmodifiableFirstValueMiss() {
        return populatedUnmodifiable.firstValue("x-nonexistent-header");
    }

    @Benchmark
    public List<String> unmodifiableAllValues() {
        return populatedUnmodifiable.allValues("content-type");
    }

    @Benchmark
    public boolean unmodifiableHasHeaderHit() {
        return populatedUnmodifiable.hasHeader("content-type");
    }

    @Benchmark
    public Map<String, List<String>> unmodifiableToMap() {
        return populatedUnmodifiable.map();
    }

    @Benchmark
    public void unmodifiableIterateAll(Blackhole bh) {
        populatedUnmodifiable.forEachEntry((name, value) -> {
            bh.consume(name);
            bh.consume(value);
        });
    }

    // --- Mutation ---

    @Benchmark
    public void setHeaderOverwrite(Blackhole bh) {
        populatedModifiable.setHeader("content-type", "text/html");
        populatedModifiable.setHeader("content-type", "application/json");
        bh.consume(populatedModifiable);
    }

    @Benchmark
    public void removeAndReAdd(Blackhole bh) {
        populatedModifiable.removeHeader("content-type");
        populatedModifiable.addHeader("content-type", "application/json");
        bh.consume(populatedModifiable);
    }

    // --- Copy ---

    @Benchmark
    public ModifiableHttpHeaders copyModifiable() {
        return populatedModifiable.copy();
    }

    @Benchmark
    public HttpHeaders toUnmodifiable() {
        return populatedModifiable.toUnmodifiable();
    }

    @Benchmark
    public ModifiableHttpHeaders unmodifiableToModifiable() {
        return populatedUnmodifiable.toModifiable();
    }
}
