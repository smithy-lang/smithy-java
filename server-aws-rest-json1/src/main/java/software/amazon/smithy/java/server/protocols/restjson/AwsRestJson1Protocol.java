/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson;

import static java.util.stream.Collectors.toUnmodifiableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.binding.HttpBinding;
import software.amazon.smithy.java.http.binding.ResponseSerializer;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.HttpJob;
import software.amazon.smithy.java.server.core.Job;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.java.server.protocols.restjson.router.UriMatcherMap;
import software.amazon.smithy.java.server.protocols.restjson.router.UriMatcherMapBuilder;
import software.amazon.smithy.java.server.protocols.restjson.router.UriPattern;
import software.amazon.smithy.java.server.protocols.restjson.router.UriTreeMatcherMap;
import software.amazon.smithy.java.server.protocols.restjson.router.ValuedMatch;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.shapes.ShapeId;

final class AwsRestJson1Protocol extends ServerProtocol {

    private static final Context.Key<
            ValuedMatch<Operation<? extends SerializableStruct, ? extends SerializableStruct>>> MATCH_KEY = Context
                    .key("Aws Rest Json1 Valued Match");

    private final Codec codec;
    private final Map<String, UriMatcherMap<Operation<?, ?>>> httpMethodToMatchersMap;
    private final HttpBinding httpBinding = new HttpBinding();

    AwsRestJson1Protocol(List<Service> services) {
        super(services);
        var httpMethodToMatchers = new HashMap<String, UriMatcherMapBuilder<Operation<?, ?>>>();
        for (Service service : services) {
            for (var operation : service.getAllOperations()) {
                var httpTrait = operation.getApiOperation()
                        .schema()
                        .expectTrait(TraitKey.HTTP_TRAIT);
                String method = httpTrait.getMethod();
                String pattern = httpTrait
                        .getUri()
                        .toString();
                httpMethodToMatchers.computeIfAbsent(method, k -> UriTreeMatcherMap.builder())
                        .add(UriPattern.forSpecificityRouting(pattern), operation);
            }
        }
        this.httpMethodToMatchersMap = httpMethodToMatchers.entrySet()
                .stream()
                .collect(toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().build()));
        this.codec = JsonCodec.builder()
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();
    }

    @Override
    public ShapeId getProtocolId() {
        return RestJson1Trait.ID;
    }

    @Override
    public ServiceProtocolResolutionResult resolveOperation(
            ServiceProtocolResolutionRequest request,
            List<Service> candidates
    ) {
        var uri = request.uri().getPath();
        String rawQuery = request.uri().getRawQuery();
        if (rawQuery != null) {
            uri += "?" + rawQuery;
        }
        var matcher = httpMethodToMatchersMap.get(request.method());
        if (matcher == null) {
            return null;
        }
        ValuedMatch<Operation<? extends SerializableStruct, ? extends SerializableStruct>> selectedOperation = matcher
                .match(uri);
        if (selectedOperation == null) {
            return null;
        }
        request.requestContext().put(MATCH_KEY, selectedOperation);

        return new ServiceProtocolResolutionResult(
                selectedOperation.getValue().getOwningService(),
                selectedOperation.getValue(),
                this);

    }

    @Override
    public CompletableFuture<ShapeDeserializer> getDeserializer(
            Job job
    ) {
        if (!job.isHttpJob()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Unsupported Job type. Only HttpJob is supported"));
        }

        var httpJob = job.asHttpJob();
        var selectedOperation = job.request().context().get(MATCH_KEY);
        Map<String, String> labelValues = new HashMap<>();
        for (SmithyPattern.Segment label : job.operation()
                .getApiOperation()
                .schema()
                .expectTrait(TraitKey.HTTP_TRAIT)
                .getUri()
                .getLabels()) {
            var values = selectedOperation.getLabelValues(label.getContent());
            if (values != null) {
                labelValues.put(label.getContent(), URLEncoding.urlDecode(values.get(0)));
            }
        }
        HttpHeaders headers = httpJob.request().headers();
        return httpBinding
                .requestDeserializer()
                .pathLabelValues(labelValues)
                .schema(job.operation().getApiOperation().inputSchema())
                .request(
                        HttpRequest.builder()
                                .headers(headers)
                                .uri(httpJob.request().uri())
                                .method(httpJob.request().method())
                                .body(job.request().getDataStream())
                                .build())
                .payloadCodec(codec)
                .payloadMediaType("application/json")
                .build();
    }

    @Override
    public CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
        HttpJob httpJob = (HttpJob) job; //We already check in the deserializeInput method.
        ResponseSerializer serializer = httpBinding.responseSerializer()
                .operation(job.operation().getApiOperation())
                .payloadCodec(codec)
                .payloadMediaType("application/json")
                .shapeValue(output);
        if (isError) {
            serializer.errorSchema(output.schema());
        }
        HttpResponse response = serializer.serializeResponse();
        httpJob.response().setSerializedValue(response.body());
        httpJob.response().setStatusCode(response.statusCode());
        httpJob.response().headers().putHeaders(response.headers().map());
        return CompletableFuture.completedFuture(null);
    }
}
