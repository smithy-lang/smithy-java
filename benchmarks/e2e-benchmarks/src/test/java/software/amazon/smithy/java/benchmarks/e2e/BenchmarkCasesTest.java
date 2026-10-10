/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.Schema;

class BenchmarkCasesTest {

    private record Stub(MockHttpTransport transport, BenchmarkClient client) {}

    private static final Map<BenchmarkProtocol, Stub> STUBS = new EnumMap<>(BenchmarkProtocol.class);

    @AfterAll
    static void closeClients() {
        STUBS.values().forEach(stub -> stub.client().close());
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
        // The model stores object bodies as base64. Content-Length must describe the decoded bytes.
        var put = BenchmarkCases.build("restXml_PutObject_L");
        var stub = stub(put.protocol());
        var call = stub.client().prepare(put);
        stub.transport().respondWith(put.response());
        call.invoke();
        assertThat(stub.transport().lastRequestBodyBytes()).isEqualTo(256_000);
        assertThat(stub.transport().lastRequest().headers().contentLength()).isEqualTo(256_000L);
        assertThat(BenchmarkCases.build("restXml_GetObject_L").response().bodyLength()).isEqualTo(256_000);
        assertThat(BenchmarkCases.build("restXml_GetObject_S").response().bodyLength()).isEqualTo(1);
        assertThat(BenchmarkCases.build("restJson1_GetObject_M").response().bodyLength()).isEqualTo(1_000);
    }

    @Test
    void cannedResponsesSerializeToCompleteHttp1Messages() {
        var response = BenchmarkCases.build("awsJson1_0_GetItemOutput_S").response();
        byte[] wire = response.toHttp1Bytes();
        String text = new String(wire, StandardCharsets.ISO_8859_1);
        assertThat(text).startsWith("HTTP/1.1 200 OK\r\n")
                .contains("\r\ncontent-type: application/x-amz-json-1.0\r\n")
                .contains("\r\ncontent-length: " + response.bodyLength() + "\r\n")
                .contains("\r\n\r\n");
        assertThat(wire.length - text.indexOf("\r\n\r\n") - 4).isEqualTo(response.bodyLength());
    }

    private static void runOnce(String id) throws Throwable {
        var benchmarkCase = BenchmarkCases.build(id);
        assertThat(benchmarkCase.id()).isEqualTo(id);
        assertThat(benchmarkCase.protocol()).isEqualTo(BenchmarkProtocol.forBenchmarkId(id));

        var stub = stub(benchmarkCase.protocol());
        var call = stub.client().prepare(benchmarkCase);
        stub.transport().respondWith(benchmarkCase.response());

        var output = call.invoke();

        assertThat(output).as("%s produced an output", id).isNotNull();
        var request = stub.transport().lastRequest();
        assertThat(request.uri().toString()).as("%s used the static endpoint", id)
                .startsWith(BenchmarkProtocol.ENDPOINT);
        assertThat(request.headers().firstValue("authorization")).as("%s was SigV4-signed", id)
                .startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/")
                .doesNotContain("x-amz-security-token");
        assertThat(request.headers().firstValue("x-amz-date")).as("%s carries x-amz-date", id).isNotBlank();
        if (sendsABody(id)) {
            assertThat(stub.transport().lastRequestBodyBytes()).as("%s serialized a request body", id).isPositive();
        }
        // Baseline and Example responses may leave all output members unset.
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

    private static Stub stub(BenchmarkProtocol protocol) {
        return STUBS.computeIfAbsent(protocol, p -> {
            var transport = new MockHttpTransport();
            return new Stub(transport, new BenchmarkClient(p, transport, transport.endpoint()));
        });
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
