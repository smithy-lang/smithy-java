/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.model.shapes.ShapeId;

public final class ProtocolResolver {

    private static final Map<ShapeId, ServerProtocolProvider> SERVER_PROTOCOL_HANDLERS = ServiceLoader.load(
            ServerProtocolProvider.class,
            ProtocolResolver.class.getClassLoader())
            .stream()
            .map(ServiceLoader.Provider::get)
            .collect(Collectors.toMap(ServerProtocolProvider::getProtocolId, Function.identity()));

    private final List<? extends ServerProtocol> serverProtocolHandlers;
    private final ServiceMatcher serviceMatcher;

    public ProtocolResolver(ServiceMatcher serviceMatcher) {
        serverProtocolHandlers = SERVER_PROTOCOL_HANDLERS.values()
                .stream()
                .sorted(Comparator.comparing(ServerProtocolProvider::precision))
                .map(p -> p.provideProtocolHandler(serviceMatcher.getAllServices()))
                .toList();
        this.serviceMatcher = serviceMatcher;
    }

    public ServiceProtocolResolutionResult resolve(ServiceProtocolResolutionRequest request) {
        var candidates = serviceMatcher.getCandidateServices(request);
        if (candidates.isEmpty()) {
            throw UnknownOperationException.builder().message("No matching services found for request").build();
        }
        for (ServerProtocol protocol : serverProtocolHandlers) {
            var resolutionResult = protocol.resolveOperation(request, candidates);
            if (resolutionResult != null) {
                return resolutionResult;
            }
        }
        throw UnknownOperationException.builder().message("No matching operations found for request").build();
    }
}
