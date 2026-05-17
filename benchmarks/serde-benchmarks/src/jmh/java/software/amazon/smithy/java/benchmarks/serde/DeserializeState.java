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

/**
 * Reusable per-trial state for a deserialization benchmark.
 *
 * <p>Each benchmark drives {@code ClientProtocol#deserializeResponse(operation,
 * context, typeRegistry, request, response)}. This state object holds those
 * arguments, pre-built once during {@code @Setup}, including a fully formed
 * {@link HttpResponse} carrying the test case's status code, headers, and
 * body.
 *
 * <p>For protocols whose response bodies are stored base64-encoded in the
 * smithy model (i.e. CBOR), the body is decoded once at setup; the benchmark
 * loop measures only deserialization.
 *
 * <p>When a test case has no body, the empty-body fallback is used. For XML
 * protocols, the fallback is auto-derived from the output shape's simple
 * name ({@code <ShapeName/>}) so the deserializer's element-name check
 * passes. For JSON protocols, pass {@code "{}".getBytes(UTF_8)}; for CBOR
 * pass {@code {0xa0}} (empty-map encoding).
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
        // Some HTTP-binding deserializers consult the request URI; provide
        // a simple stub.
        this.request = HttpRequest.create()
                .setMethod("POST")
                .setUri(ENDPOINT);
    }

    /**
     * Build the deserialize state from a response test case.
     *
     * @param testCaseId       benchmark test case id
     * @param generatedPackage Java package emitted by the protocol's codegen
     *                         projection
     * @param emptyBody        bytes to use when the test case body is absent.
     *                         Pass {@code null} for an XML-style default
     *                         derived from the output shape name.
     * @param contentType      content-type header value (e.g.
     *                         {@code "application/json"})
     * @param base64DecodeBody whether the body string in the test trait is
     *                         base64-encoded (CBOR convention) and should be
     *                         decoded before being placed in the response
     */
    static DeserializeState forTestCase(
            String testCaseId,
            String generatedPackage,
            byte[] emptyBody,
            String contentType,
            boolean base64DecodeBody
    ) {
        ResponseEntry entry = BenchmarkTestCases.response(testCaseId);
        OperationShape opShape = entry.operation();

        ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation =
                SerializeState.resolveOperation(generatedPackage, opShape.getId().getName());

        byte[] resolvedEmpty = emptyBody;
        if (resolvedEmpty == null) {
            // Default empty XML body uses the output shape's wire name as
            // the root element. This matches what an XML protocol response
            // with header-only bindings looks like on the wire. The wire
            // name comes from @xmlName if present, otherwise the shape's
            // simple name.
            var schema = operation.outputSchema();
            var xmlName = schema.getTrait(TraitKey.XML_NAME_TRAIT);
            String rootName = xmlName != null ? xmlName.getValue() : schema.id().getName();
            resolvedEmpty = ("<" + rootName + "/>").getBytes(StandardCharsets.UTF_8);
        }
        byte[] body = entry.testCase()
                .getBody()
                .map(s -> base64DecodeBody
                        ? Base64.getMimeDecoder().decode(s)
                        : s.getBytes(StandardCharsets.UTF_8))
                .orElse(resolvedEmpty);
        int statusCode = entry.testCase().getCode();

        ModifiableHttpResponse response = HttpResponse.create()
                .setStatusCode(statusCode)
                .setHeader(HeaderName.CONTENT_TYPE.toString(), contentType);

        // Merge the trait's response.headers (if any) so the binding
        // deserializer's @httpHeader extraction sees realistic headers.
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
