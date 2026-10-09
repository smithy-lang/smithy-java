/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestDocument;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait;

/**
 * Builds independent request and response benchmarks from the model's {@code serde-benchmark} test cases.
 */
final class BenchmarkCases {

    static final String TAG = "serde-benchmark";

    private static final String CANONICAL_RESOURCE = "canonical-benchmarks.txt";

    private static final Model MODEL = Model.assembler(BenchmarkCases.class.getClassLoader())
            .discoverModels(BenchmarkCases.class.getClassLoader())
            .assemble()
            .unwrap();

    private static final Map<String, RequestEntry> REQUESTS = indexRequests();
    private static final Map<String, ResponseEntry> RESPONSES = indexResponses();
    private static final List<String> CANONICAL = loadCanonical();

    private BenchmarkCases() {}

    static Model model() {
        return MODEL;
    }

    /** The 71 cross-SDK benchmark ids, in reporting order. */
    static List<String> canonicalIds() {
        return CANONICAL;
    }

    /** Every tagged case in the model: the canonical ids first, then smithy-java-only cases alphabetically. */
    static List<String> allIds() {
        var extras = new TreeSet<String>();
        extras.addAll(REQUESTS.keySet());
        extras.addAll(RESPONSES.keySet());
        CANONICAL.forEach(extras::remove);
        var all = new ArrayList<>(CANONICAL);
        all.addAll(extras);
        return all;
    }

    static boolean exists(String id) {
        return REQUESTS.containsKey(id) || RESPONSES.containsKey(id);
    }

    /** Builds the case for a benchmark id: generated operation, typed input, and canned response. */
    static BenchmarkCase build(String id) {
        var protocol = BenchmarkProtocol.forBenchmarkId(id);
        var request = REQUESTS.get(id);
        if (request != null) {
            var operation = resolveOperation(protocol, request.operation());
            var input = buildInput(operation, request.testCase().getParams());
            int code = request.operation().getTrait(HttpTrait.class).map(HttpTrait::getCode).orElse(200);
            var response = CannedResponse.of(
                    code,
                    Map.of(),
                    protocol.minimalResponseBody(request.operation().getId().getName()),
                    protocol.contentType());
            return new BenchmarkCase(
                    id,
                    protocol,
                    request.operation().getId().getName(),
                    BenchmarkCase.Source.REQUEST,
                    operation,
                    input,
                    response,
                    outputPayloadMember(operation));
        }
        var response = RESPONSES.get(id);
        if (response != null) {
            var operation = resolveOperation(protocol, response.operation());
            var input = buildInput(operation, MinimalInput.forOperation(MODEL, response.operation()));
            // CBOR and blob payload fixtures are base64; their Content-Length describes the decoded bytes.
            boolean blobPayload = blobPayloadMember(operation.outputSchema()) != null;
            byte[] body = response.testCase()
                    .getBody()
                    .map(s -> protocol.base64Bodies() || blobPayload
                            ? Base64.getMimeDecoder().decode(s)
                            : s.getBytes(StandardCharsets.UTF_8))
                    .orElse(protocol.emptyResponseBody());
            var canned = CannedResponse.of(
                    response.testCase().getCode(),
                    response.testCase().getHeaders(),
                    body,
                    protocol.contentType());
            return new BenchmarkCase(
                    id,
                    protocol,
                    response.operation().getId().getName(),
                    BenchmarkCase.Source.RESPONSE,
                    operation,
                    input,
                    canned,
                    outputPayloadMember(operation));
        }
        throw new IllegalArgumentException(
                "No @httpRequestTests or @httpResponseTests case with id '" + id + "' is tagged " + TAG);
    }

    @SuppressWarnings("unchecked")
    private static ApiOperation<SerializableStruct, SerializableStruct> resolveOperation(
            BenchmarkProtocol protocol,
            OperationShape operation
    ) {
        String fqcn = protocol.generatedPackage() + ".model." + operation.getId().getName();
        try {
            Method instance = Class.forName(fqcn).getMethod("instance");
            return (ApiOperation<SerializableStruct, SerializableStruct>) instance.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve generated ApiOperation " + fqcn, e);
        }
    }

    private static SerializableStruct buildInput(ApiOperation<SerializableStruct, SerializableStruct> op, Node params) {
        var builder = op.inputBuilder();
        Node effective = params == null ? Node.objectNode() : params;
        new ProtocolTestDocument(effective, null).deserializeInto(builder);
        // Structured blobs retain protocol-test UTF-8 semantics. Only @httpPayload blobs are base64 in this
        // model: ContentLength and CRC64NVME describe the decoded bytes, as in the Java v2 harness.
        Schema payload = blobPayloadMember(op.inputSchema());
        if (payload != null && effective.isObjectNode()) {
            effective.expectObjectNode().getStringMember(payload.memberName()).ifPresent(value -> {
                byte[] decoded = Base64.getMimeDecoder().decode(value.getValue());
                builder.setMemberValue(payload,
                        isStreaming(payload) ? DataStream.ofBytes(decoded) : ByteBuffer.wrap(decoded));
            });
        }
        return builder.build();
    }

    /** The output's blob {@code @httpPayload} member, streaming or not, which the benchmark consumes fully. */
    private static Schema outputPayloadMember(ApiOperation<?, ?> operation) {
        return blobPayloadMember(operation.outputSchema());
    }

    /** The {@code @httpPayload} member of a structure when it is a blob, streaming or not; null otherwise. */
    private static Schema blobPayloadMember(Schema struct) {
        for (Schema member : struct.members()) {
            if (member.hasTrait(TraitKey.HTTP_PAYLOAD_TRAIT) && member.type() == ShapeType.BLOB) {
                return member;
            }
        }
        return null;
    }

    private static boolean isStreaming(Schema member) {
        return member.hasTrait(TraitKey.STREAMING_TRAIT) || member.memberTarget().hasTrait(TraitKey.STREAMING_TRAIT);
    }

    private static Map<String, RequestEntry> indexRequests() {
        Map<String, RequestEntry> result = new LinkedHashMap<>();
        for (OperationShape op : MODEL.getOperationShapes()) {
            op.getTrait(HttpRequestTestsTrait.class).ifPresent(trait -> {
                for (HttpRequestTestCase tc : trait.getTestCases()) {
                    if (hasBenchmarkTag(tc)) {
                        result.put(tc.getId(), new RequestEntry(op, tc));
                    }
                }
            });
        }
        return result;
    }

    private static Map<String, ResponseEntry> indexResponses() {
        Map<String, ResponseEntry> result = new LinkedHashMap<>();
        for (OperationShape op : MODEL.getOperationShapes()) {
            op.getTrait(HttpResponseTestsTrait.class).ifPresent(trait -> {
                for (HttpResponseTestCase tc : trait.getTestCases()) {
                    if (hasBenchmarkTag(tc)) {
                        result.put(tc.getId(), new ResponseEntry(op, tc));
                    }
                }
            });
        }
        return result;
    }

    private static boolean hasBenchmarkTag(HttpMessageTestCase tc) {
        return tc.getTags().contains(TAG);
    }

    private static List<String> loadCanonical() {
        List<String> ids = new ArrayList<>();
        try (InputStream in = BenchmarkCases.class.getResourceAsStream(CANONICAL_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + CANONICAL_RESOURCE);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String id = line.strip();
                if (id.isEmpty() || id.startsWith("#")) {
                    continue;
                }
                if (!exists(id)) {
                    throw new IllegalStateException("Canonical benchmark '" + id + "' is not a " + TAG
                            + "-tagged test case in the model");
                }
                ids.add(id);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(ids);
    }

    record RequestEntry(OperationShape operation, HttpRequestTestCase testCase) {}

    record ResponseEntry(OperationShape operation, HttpResponseTestCase testCase) {}
}
