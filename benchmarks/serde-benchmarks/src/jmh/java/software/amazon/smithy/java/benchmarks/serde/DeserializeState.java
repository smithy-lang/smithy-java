/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import software.amazon.smithy.java.benchmarks.serde.BenchmarkTestCases.ResponseEntry;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.ModifiableHttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Reusable per-trial state for a deserialization benchmark.
 *
 * <p>Each benchmark drives {@code ClientProtocol#deserializeResponse(operation,
 * context, typeRegistry, request, response)}. This state object holds those
 * arguments, pre-built once during {@code @Setup}, including a fully formed
 * {@link HttpResponse} carrying the test case's status code, headers, and body.
 *
 * <p>Base64 decoding and JSON whitespace normalization happen once at setup;
 * the benchmark loop measures only deserialization.
 *
 * <p>When a test case has no body, the empty-body fallback is used. For XML
 * protocols, a null fallback is auto-derived from the output shape's wire
 * name ({@code <ShapeName/>}) so the deserializer's element-name check passes.
 * For JSON protocols, pass {@code "{}".getBytes(UTF_8)}; for CBOR pass
 * {@code {0xa0}} (empty-map encoding).
 */
final class DeserializeState {

    private static final SmithyUri ENDPOINT = SmithyUri.of("http://localhost/");

    final ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation;
    final HttpRequest request;
    final HttpResponse response;
    final Context context;
    final TypeRegistry typeRegistry;

    private DeserializeState(
            ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation,
            HttpResponse response
    ) {
        this.operation = operation;
        this.response = response;
        this.context = Context.create();
        this.typeRegistry = TypeRegistry.empty();
        // Some HTTP-binding deserializers consult the request URI; provide a simple stub.
        this.request = HttpRequest.create()
                .setMethod("POST")
                .setUri(ENDPOINT);
    }

    /**
     * Build the deserialize state from a response test case.
     *
     * <p>JSON formatting whitespace is removed based on the test case's body media
     * type, then its Content-Type header, then the protocol's default content type.
     * Trait response headers override defaults so HTTP header bindings see the
     * values specified by the test case.
     *
     * @param testCaseId benchmark test case id
     * @param generatedPackage Java package emitted by the protocol's codegen projection
     * @param serviceId service whose operation is being benchmarked
     * @param emptyBody bytes to use when the test case body is absent.
     *        Pass {@code null} for an XML-style default derived from the output
     *        shape's {@code @xmlName}, or its simple name if that trait is absent.
     * @param contentType default content-type header value (e.g. {@code "application/json"})
     * @param base64DecodeBody whether the body string in the test trait is
     *        base64-encoded and should be decoded before being placed in the response,
     *        regardless of protocol. Decoded bodies are not minified.
     */
    static DeserializeState forTestCase(
            String testCaseId,
            String generatedPackage,
            ShapeId serviceId,
            byte[] emptyBody,
            String contentType,
            boolean base64DecodeBody
    ) {
        ResponseEntry entry = BenchmarkTestCases.response(testCaseId);
        OperationShape opShape = entry.operation();

        ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation =
                BenchmarkOperations.resolve(opShape, generatedPackage, serviceId);

        byte[] resolvedEmpty = emptyBody;
        if (resolvedEmpty == null) {
            // Header-only XML responses still need the output shape's wire name as the root element.
            var schema = operation.outputSchema();
            var xmlName = schema.getTrait(TraitKey.XML_NAME_TRAIT);
            String rootName = xmlName != null ? xmlName.getValue() : schema.id().getName();
            resolvedEmpty = ("<" + rootName + "/>").getBytes(StandardCharsets.UTF_8);
        }
        byte[] body = entry.testCase()
                .getBody()
                .map(s -> base64DecodeBody
                        ? Base64.getMimeDecoder().decode(s)
                        : WireBodyNormalizer
                                .normalize(s.getBytes(StandardCharsets.UTF_8), entry.testCase(), contentType))
                .orElse(resolvedEmpty);
        int statusCode = entry.testCase().getCode();

        ModifiableHttpResponse response = HttpResponse.create()
                .setStatusCode(statusCode)
                .setHeader(HeaderName.CONTENT_TYPE.toString(), contentType);

        // Merge trait headers so @httpHeader bindings see realistic response values.
        Map<String, String> traitHeaders = entry.testCase().getHeaders();
        if (traitHeaders != null) {
            for (Map.Entry<String, String> e : traitHeaders.entrySet()) {
                response.setHeader(e.getKey(), e.getValue());
            }
        }
        response.setBody(DataStream.ofBytes(body, contentType));

        return new DeserializeState(operation, response);
    }
}
