/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.Schema;

/**
 * Every benchmark must run end to end through its generated client exactly once per call, with no retries, and
 * produce a populated output. A benchmark that throws would fail a real run, so it fails here first.
 */
class BenchmarkCasesTest {

    private static final Map<BenchmarkProtocol, BenchmarkClient> CLIENTS = new EnumMap<>(BenchmarkProtocol.class);

    @AfterAll
    static void closeClients() {
        CLIENTS.values().forEach(BenchmarkClient::close);
    }

    static List<String> canonicalIds() {
        return BenchmarkCases.canonicalIds();
    }

    static List<String> smithyJavaOnlyIds() {
        List<String> extras = new ArrayList<>(BenchmarkCases.allIds());
        extras.removeAll(BenchmarkCases.canonicalIds());
        return extras;
    }

    @Test
    void canonicalSetIsTheCrossSdkSeventyOne() {
        var ids = BenchmarkCases.canonicalIds();
        assertThat(ids).hasSize(71).doesNotHaveDuplicates();
        assertThat(count(ids, BenchmarkProtocol.AWS_JSON_1_0)).isEqualTo(22);
        assertThat(count(ids, BenchmarkProtocol.RPC_V2_CBOR)).isEqualTo(19);
        assertThat(count(ids, BenchmarkProtocol.AWS_QUERY)).isEqualTo(10);
        assertThat(count(ids, BenchmarkProtocol.REST_JSON_1)).isEqualTo(10);
        assertThat(count(ids, BenchmarkProtocol.REST_XML)).isEqualTo(10);
    }

    @Test
    void allIdsStartWithTheCanonicalSet() {
        var all = BenchmarkCases.allIds();
        assertThat(all.subList(0, 71)).isEqualTo(BenchmarkCases.canonicalIds());
        assertThat(all).doesNotHaveDuplicates();
        assertThat(smithyJavaOnlyIds()).allMatch(id -> id.contains("WideTypes") || id.contains("OutOfOrder"));
    }

    @ParameterizedTest
    @MethodSource("canonicalIds")
    void canonicalBenchmarkRunsOnce(String id) throws Throwable {
        runOnce(id);
    }

    @ParameterizedTest
    @MethodSource("smithyJavaOnlyIds")
    void smithyJavaOnlyBenchmarkRunsOnce(String id) throws Throwable {
        runOnce(id);
    }

    @Test
    void objectPayloadsAreTheDecodedBytes() throws Throwable {
        // The model stores S3 object bodies base64-encoded and its ContentLength / Content-Length values describe
        // the decoded bytes, so the request the harness sends and the response it serves must be those bytes
        // (Java v2 measures the same sizes). Sending the base64 text produced a 341,336-byte body behind a
        // 256,000-byte Content-Length, which real fixture servers rejected.
        var put = BenchmarkCases.build("restXml_PutObject_L");
        var client = new BenchmarkClient(put.protocol(), new MockHttpTransport(), BenchmarkProtocol.ENDPOINT);
        var call = client.prepare(put);
        client.transport().resetCounters();
        call.invoke();
        assertThat(client.transport().requestBodyBytes()).isEqualTo(256_000);
        assertThat(client.transport().lastRequest().headers().contentLength()).isEqualTo(256_000L);
        assertThat(BenchmarkCases.build("restXml_GetObject_L").response().bodyLength()).isEqualTo(256_000);
        assertThat(BenchmarkCases.build("restXml_GetObject_S").response().bodyLength()).isEqualTo(1);
        assertThat(BenchmarkCases.build("restJson1_GetObject_M").response().bodyLength()).isEqualTo(1_000);
    }

    private static void runOnce(String id) throws Throwable {
        var benchmarkCase = BenchmarkCases.build(id);
        assertThat(benchmarkCase.id()).isEqualTo(id);
        assertThat(benchmarkCase.protocol()).isEqualTo(BenchmarkProtocol.forBenchmarkId(id));

        var client = CLIENTS.computeIfAbsent(
                benchmarkCase.protocol(),
                protocol -> new BenchmarkClient(protocol, new MockHttpTransport(), BenchmarkProtocol.ENDPOINT));
        var call = client.prepare(benchmarkCase);
        client.transport().resetCounters();

        var output = call.invoke();
        // What the harness checks before timing anything: expected status, body length, and a request whose body
        // matches its own Content-Length (the stub would otherwise accept a request a real server rejects).
        client.transport().validateLast(benchmarkCase);

        assertThat(output).as("%s produced an output", id).isNotNull();
        assertThat(client.transport().requests()).as("%s made exactly one request (no retries)", id).isEqualTo(1);
        var request = client.transport().lastRequest();
        assertThat(request.uri().toString()).as("%s used the static endpoint", id)
                .startsWith(BenchmarkProtocol.ENDPOINT);
        // Signed with the harness's static credentials, never with ambient credentials from the host.
        assertThat(request.headers().firstValue("authorization")).as("%s was SigV4-signed", id)
                .startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/")
                .doesNotContain("x-amz-security-token");
        assertThat(request.headers().firstValue("x-amz-date")).as("%s carries x-amz-date", id).isNotBlank();
        if (sendsABody(id)) {
            assertThat(client.transport().requestBodyBytes()).as("%s serialized a request body", id).isPositive();
        }
        // Baseline/Example response cases model the smallest possible response, which may carry no body at all
        // and leave every output member unset. Payload-bearing cases must actually populate the output.
        boolean minimalResponse = id.endsWith("_Baseline") || id.endsWith("_Example");
        if (benchmarkCase.source() == BenchmarkCase.Source.RESPONSE && !minimalResponse) {
            assertThat(benchmarkCase.response().bodyLength()).as("%s has a canned response body", id).isPositive();
            for (String member : List.of("Item", "MetricDataResults", "CopyObjectResult", "Body")) {
                Schema schema = benchmarkCase.operation().outputSchema().member(member);
                if (schema != null) {
                    assertThat((Object) output.getMemberValue(schema))
                            .as("%s deserialized output member %s", id, member)
                            .isNotNull();
                }
            }
        }
    }

    private static boolean sendsABody(String id) {
        return id.contains("PutItemRequest")
                || id.contains("PutObject")
                || id.contains("PutMetricDataRequest")
                || id.contains("GetMetricDataRequest")
                || id.contains("GetItemInput")
                || id.contains("WideTypesRequest");
    }

    private static long count(List<String> ids, BenchmarkProtocol protocol) {
        return ids.stream().filter(id -> BenchmarkProtocol.forBenchmarkId(id) == protocol).count();
    }
}
