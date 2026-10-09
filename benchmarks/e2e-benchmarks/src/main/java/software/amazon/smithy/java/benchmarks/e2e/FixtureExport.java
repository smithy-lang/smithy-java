/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Writes a benchmark's canned response as files a fixture server can serve: the exact body bytes plus a JSON
 * description with the status, headers, length, SHA-256 and the server arguments that reproduce the response.
 */
final class FixtureExport {

    private FixtureExport() {}

    static int run(String[] args) {
        if (args.length == 0 || args[0].startsWith("-")) {
            System.err.println("usage: export-fixture <benchmark-id> [--out DIR]");
            return 2;
        }
        String id = args[0];
        Path out = Path.of("fixtures");
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--out") && i + 1 < args.length) {
                out = Path.of(args[++i]);
            } else {
                System.err.println("error: unknown argument " + args[i]);
                return 2;
            }
        }
        if (!BenchmarkCases.exists(id)) {
            System.err.println("error: unknown benchmark id " + id);
            return 2;
        }
        var written = write(BenchmarkCases.build(id), out);
        System.out.println("Wrote " + written.get(0) + " and " + written.get(1));
        return 0;
    }

    /** Writes {@code <id>.body} and {@code <id>.fixture.json}; returns both paths. */
    static List<Path> write(BenchmarkCase benchmarkCase, Path directory) {
        var response = benchmarkCase.response();
        byte[] body = new byte[response.bodyLength()];
        response.body().duplicate().get(body);
        Path bodyFile = directory.resolve(benchmarkCase.id() + ".body");
        Path jsonFile = directory.resolve(benchmarkCase.id() + ".fixture.json");

        var headers = Node.objectNodeBuilder();
        List<String> serverArgs = new ArrayList<>();
        serverArgs.add("--body");
        serverArgs.add(bodyFile.toAbsolutePath().toString());
        serverArgs.add("--status");
        serverArgs.add(Integer.toString(response.statusCode()));
        serverArgs.add("--content-type");
        serverArgs.add(response.contentType());
        for (var entry : response.headerMap().entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            String value = String.join(",", entry.getValue());
            headers.withMember(name, value);
            if (!name.equals("content-type") && !name.equals("content-length")) {
                serverArgs.add("--header");
                serverArgs.add(name + ": " + value);
            }
        }
        ObjectNode description = Node.objectNodeBuilder()
                .withMember("id", benchmarkCase.id())
                .withMember("protocol", benchmarkCase.protocol().idPrefix())
                .withMember("operation", benchmarkCase.operationName())
                .withMember("source", benchmarkCase.source().label())
                .withMember("status", response.statusCode())
                .withMember("content_type", response.contentType())
                .withMember("headers", headers.build())
                .withMember("body_file", bodyFile.getFileName().toString())
                .withMember("body_bytes", body.length)
                .withMember("body_sha256", sha256(body))
                .withMember("request_body_bytes",
                        benchmarkCase.source() == BenchmarkCase.Source.REQUEST
                                ? requestBodyEstimate(benchmarkCase)
                                : 0)
                .withMember("server_args", ArrayNode.fromStrings(serverArgs))
                .build();
        try {
            Files.createDirectories(directory);
            Files.write(bodyFile, body);
            Files.writeString(jsonFile, Node.prettyPrintJson(description) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write fixture for " + benchmarkCase.id(), e);
        }
        return List.of(bodyFile, jsonFile);
    }

    /** Serializes the input once through the stub to report the request body size the fixture server will see. */
    private static long requestBodyEstimate(BenchmarkCase benchmarkCase) {
        try (var client =
                new BenchmarkClient(benchmarkCase.protocol(), new MockHttpTransport(), BenchmarkProtocol.ENDPOINT)) {
            var call = client.prepare(benchmarkCase);
            client.transport().resetCounters();
            call.invoke();
            return client.transport().requestBodyBytes();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
