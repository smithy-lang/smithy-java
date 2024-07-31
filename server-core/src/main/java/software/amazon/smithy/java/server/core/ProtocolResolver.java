/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

public final class ProtocolResolver {

    private static final Map<ShapeId, ServerProtocolProvider> SERVER_PROTOCOL_HANDLERS = ServiceLoader.load(
        ServerProtocolProvider.class,
        ProtocolResolver.class.getClassLoader()
    )
        .stream()
        .map(ServiceLoader.Provider::get)
        .collect(Collectors.toMap(ServerProtocolProvider::getProtocolId, Function.identity()));

    private final List<? extends ServerProtocol> serverProtocolHandlers;

    public ProtocolResolver(List<Service> services) {
        serverProtocolHandlers = SERVER_PROTOCOL_HANDLERS.values()
            .stream()
            .sorted(Comparator.<ServerProtocolProvider, Integer>comparing(ServerProtocolProvider::priority).reversed())
            .map(p -> p.provideProtocolHandler(services))
            .toList();
    }

    public ResolutionResult resolveServiceAndOperation(ResolutionRequest request) {
        return null;
    }

    public record ResolutionRequest(String path) {
    }

    public record ResolutionResult(Operation operation) {
    }
}
