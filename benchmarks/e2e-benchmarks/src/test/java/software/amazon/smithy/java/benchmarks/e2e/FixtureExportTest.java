/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.model.node.Node;

class FixtureExportTest {

    @TempDir
    Path tmp;

    @Test
    void writesTheExactResponseBytesAndServerArguments() throws Exception {
        var benchmarkCase = BenchmarkCases.build("restXml_GetObject_S");
        var written = FixtureExport.write(benchmarkCase, tmp);

        byte[] body = Files.readAllBytes(written.get(0));
        assertThat(body).hasSize(benchmarkCase.response().bodyLength());
        var json = Node.parse(Files.readString(written.get(1))).expectObjectNode();
        assertThat(json.expectNumberMember("status").getValue().intValue()).isEqualTo(200);
        assertThat(json.expectNumberMember("body_bytes").getValue().intValue()).isEqualTo(body.length);
        assertThat(json.expectStringMember("body_sha256").getValue())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
        var args = json.expectArrayMember("server_args").getElementsAs(n -> n.expectStringNode().getValue());
        assertThat(args).contains("--body", "--status", "200", "--content-type");
        assertThat(args).doesNotContain("content-length");
        assertThat(json.expectObjectMember("headers").getStringMap()).containsKey("content-type");
    }

    @Test
    void requestCasesReportTheRequestBodySize() {
        var benchmarkCase = BenchmarkCases.build("rpcv2Cbor_PutItemRequest_Baseline");
        var written = FixtureExport.write(benchmarkCase, tmp);
        var json = Node.parse(readQuietly(written.get(1))).expectObjectNode();
        assertThat(json.expectNumberMember("request_body_bytes").getValue().longValue()).isPositive();
        assertThat(json.expectStringMember("source").getValue()).isEqualTo("request");
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
