/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.Job;
import software.amazon.smithy.java.server.core.ProtocolResolver;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServerProtocolProvider;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.model.shapes.ShapeId;

public class ProtocolTestProtocolProvider implements ServerProtocolProvider {

    @Override
    public ServerProtocol provideProtocolHandler(List<Service> candidateServices) {
        return new DelegatingServerProtocol(candidateServices);
    }

    @Override
    public ShapeId getProtocolId() {
        return ShapeId.from("aws.protocols#protocolTestsDelegatingProtocol");
    }

    @Override
    public int precision() {
        return Integer.MAX_VALUE;
    }

    private static class DelegatingServerProtocol extends ServerProtocol {

        private static final Context.Key<ServerProtocol> PROTOCOL_TO_TEST = Context.key("protocol-to-test");

        private final Map<ShapeId, ServerProtocol> delegateProtocols;

        public DelegatingServerProtocol(List<Service> candidateServices) {
            super(candidateServices);
            delegateProtocols = ServiceLoader.load(
                    ServerProtocolProvider.class,
                    ProtocolResolver.class.getClassLoader())
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(p -> p.getClass() != ProtocolTestProtocolProvider.class)
                    .collect(
                            Collectors.toMap(
                                    ServerProtocolProvider::getProtocolId,
                                    s -> s.provideProtocolHandler(candidateServices)));
        }

        @Override
        public ShapeId getProtocolId() {
            return ShapeId.from("");
        }

        @Override
        public ServiceProtocolResolutionResult resolveOperation(
                ServiceProtocolResolutionRequest request,
                List<Service> candidates
        ) {
            String protocolIdHeader = request.headers().firstValue("x-protocol-test-protocol-id");
            if (protocolIdHeader != null) {
                ServerProtocol protocol = delegateProtocols.get(ShapeId.from(protocolIdHeader));
                request.requestContext().put(PROTOCOL_TO_TEST, protocol);
                ShapeId serviceId = ShapeId.from(request.headers().firstValue("x-protocol-test-service"));
                ShapeId operationId = ShapeId.from(request.headers().firstValue("x-protocol-test-operation"));
                for (var service : candidates) {
                    if (service.schema().id().equals(serviceId)) {
                        for (var operation : service.getAllOperations()) {
                            if (operation.getApiOperation().schema().id().equals(operationId)) {
                                return new ServiceProtocolResolutionResult(service, operation, this);
                            }
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public CompletableFuture<Void> deserializeInput(Job job) {
            var protocol = job.request().context().get(PROTOCOL_TO_TEST);
            if (protocol != null) {
                job.request()
                        .setDeserializedValue(
                                job.operation().getApiOperation().inputBuilder().errorCorrection().build());
                return CompletableFuture.completedFuture(null);
            }
            throw new IllegalStateException("Should not be invoked if no protocol was selected");
        }

        @Override
        public CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
            var protocol = job.request().context().get(PROTOCOL_TO_TEST);
            if (protocol != null) {
                if (isError) {
                    return protocol.serializeError(job, (ModeledException) output);
                } else {
                    return protocol.serializeOutput(job, output);
                }
            }
            throw new IllegalStateException("Should not be invoked if no protocol was selected");
        }
    }
}
