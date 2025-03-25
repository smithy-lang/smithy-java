/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.coral.smithy.java.server.protocols.jsonrpc2;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.jsonrpc2.JsonRpc2MethodTrait;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.Job;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.java.server.core.StdioRequest;
import software.amazon.smithy.java.server.core.StdioResponse;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

final class JsonRpc2Protocol extends ServerProtocol {
    static final ShapeId JSON_RPC2_PROTOCOL_ID = ShapeId.from("smithy.example#jsonRpc2");
    private static final TraitKey<JsonRpc2MethodTrait> METHOD_TRAIT = TraitKey.get(JsonRpc2MethodTrait.class);
    private static final Codec JSON_CODEC = JsonCodec.builder().build();

    // JSON-RPC has no concept of services so we need every method to be uniquely named
    private final Map<String, Operation<?, ?>> operationsByMethod;

    JsonRpc2Protocol(List<Service> services) {
        super(services);
        operationsByMethod = services.stream()
                .flatMap(s -> s.getAllOperations().stream())
                .collect(Collectors.toMap(
                        op -> op.getApiOperation()
                                .schema()
                                .expectTrait(METHOD_TRAIT)
                                .method(),
                        Function.identity()));
    }

    @Override
    public ShapeId getProtocolId() {
        return JSON_RPC2_PROTOCOL_ID;
    }

    @Override
    public ServiceProtocolResolutionResult resolveOperation(
            ServiceProtocolResolutionRequest request,
            List<Service> candidates
    ) {
        var op = operationsByMethod.get(request.method());
        if (op == null) {
            return null;
        }
        return new ServiceProtocolResolutionResult(op.getOwningService(), op, this);
    }

    @Override
    public CompletableFuture<Void> deserializeInput(Job job) {
        // TODO: HTTP support
        var params = ((StdioRequest) job.request()).params();
        if (params != null) {
            var apiInput = params
                    .asShape(job.operation()
                            .getApiOperation()
                            .inputBuilder());
            job.request().setDeserializedValue(apiInput);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
        // TODO: HTTP support
        ((StdioResponse) job.response()).setResponse(output);
        return CompletableFuture.completedFuture(null);
    }
}
