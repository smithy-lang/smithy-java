/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.binding.HttpBinding;
import software.amazon.smithy.java.runtime.http.binding.ResponseSerializer;
import software.amazon.smithy.java.runtime.io.uri.URLEncoding;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.HttpJob;
import software.amazon.smithy.java.server.core.Job;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.java.server.protocols.restjson.router.UriMatcherMap;
import software.amazon.smithy.java.server.protocols.restjson.router.UriPattern;
import software.amazon.smithy.java.server.protocols.restjson.router.UriTreeMatcherMap;
import software.amazon.smithy.java.server.protocols.restjson.router.ValuedMatch;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.shapes.ShapeId;

final class AwsRestJson1Protocol extends ServerProtocol {

    private static final Context.Key<ValuedMatch<Operation<? extends SerializableStruct, ? extends SerializableStruct>>> MATCH_KEY = Context
        .key("Aws Rest Json1 Valued Match");

    private final Codec codec;
    private final UriMatcherMap<Operation<?, ?>> matcher;
    private final HttpBinding httpBinding = new HttpBinding();

    AwsRestJson1Protocol(List<Service> services) {
        super(services);
        var matcherBuilder = UriTreeMatcherMap.<Operation<?, ?>>builder();
        for (Service service : services) {
            for (var operation : service.getAllOperations()) {
                String pattern = operation.getApiOperation()
                    .schema()
                    .expectTrait(TraitKey.HTTP_TRAIT)
                    .getUri()
                    .toString();
                matcherBuilder.add(UriPattern.forSpecificityRouting(pattern), operation);
            }
        }
        this.matcher = matcherBuilder.build();
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
        ValuedMatch<Operation<? extends SerializableStruct, ? extends SerializableStruct>> selectedOperation = matcher
            .match(uri);
        if (selectedOperation == null) {
            return null;
        }
        request.requestContext().put(MATCH_KEY, selectedOperation);

        return new ServiceProtocolResolutionResult(
            selectedOperation.getValue().getOwningService(),
            selectedOperation.getValue(),
            this
        );

    }

    @Override
    public CompletableFuture<Void> deserializeInput(Job job) {
        if (!job.isHttpJob()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Unsupported Job type. Only HttpJob is supported")
            );
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
        var inputShapeBuilder = job.operation().getApiOperation().inputBuilder();
        var deser = httpBinding
            .requestDeserializer()
            .inputShapeBuilder(inputShapeBuilder)
            .pathLabelValues(labelValues)
            .request(
                SmithyHttpRequest.builder()
                    .headers(headers)
                    .uri(httpJob.request().uri())
                    .method(httpJob.request().method())
                    .body(job.request().getDataStream())
                    .build()
            )
            .payloadCodec(codec)
            .payloadMediaType("application/json");

        try {
            deser.deserialize().get();
        } catch (Exception e) {
            //TODO do exception translation.
            return CompletableFuture.failedFuture(e);
        }

        job.request().setDeserializedValue(inputShapeBuilder.build());
        return CompletableFuture.completedFuture(null);

    }

    @Override
    public CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output) {
        HttpJob httpJob = (HttpJob) job; //We already check in the deserializeInput method.
        ResponseSerializer serializer = httpBinding.responseSerializer()
            .operation(job.operation().getApiOperation())
            .payloadCodec(codec)
            .payloadMediaType("application/json")
            .shapeValue(output);
        if (output instanceof ModeledApiException mae) {
            serializer = serializer.errorSchema(mae.schema());
        }
        SmithyHttpResponse response = serializer.serializeResponse();
        httpJob.response().setSerializedValue(response.body());
        httpJob.response().setStatusCode(response.statusCode());
        httpJob.response().headers().putHeaders(response.headers().map());
        return CompletableFuture.completedFuture(null);
    }
}
