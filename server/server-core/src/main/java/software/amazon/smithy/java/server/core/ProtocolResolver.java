/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Iterates a precision-ordered list of {@link ServerProtocol}s and returns
 * the first that claims the request, per the
 * <a href="https://smithy.io/2.0/guides/wire-protocol-selection.html">Smithy 2.0
 * Wire protocol selection</a> guide.
 *
 * <p>Two resolution methods are exposed for two caller styles:
 *
 * <ul>
 *   <li>{@link #resolve(ServiceProtocolResolutionRequest)} — throws
 *       {@link UnknownOperationException} when no protocol claims the request
 *       <em>or</em> when a protocol claims it but rejects it as malformed.
 *       Used by transports that always answer 404 in either case
 *       (e.g. {@code :server:server-netty}).</li>
 *   <li>{@link #resolveOrEmpty(ServiceProtocolResolutionRequest)} — distinguishes
 *       <em>no-claim</em> (returns {@link Optional#empty()}, leaving the caller
 *       free to delegate elsewhere) from <em>claim-and-reject</em> (still throws
 *       {@code UnknownOperationException}). Used by transports that share a
 *       router with non-Smithy handlers (e.g. {@code :server:server-vertx}
 *       falling through via {@code ctx.next()}).</li>
 * </ul>
 *
 * <p>Two constructors are exposed for two SPI-loading regimes:
 *
 * <ul>
 *   <li>{@link #ProtocolResolver(ServiceMatcher)} — loads
 *       {@link ServerProtocolProvider}s from the static class-init
 *       {@link ServiceLoader} cache (rooted on this class's classloader).
 *       Suitable for transports that share a classloader with the
 *       protocol jars (e.g. {@code :server:server-netty} on a flat
 *       classpath).</li>
 *   <li>{@link #ProtocolResolver(ServiceMatcher, List)} — accepts a
 *       caller-supplied, precision-sorted protocol list. Suitable for
 *       container/framework deployments where runtime jars live in a
 *       different classloader than the resolver class
 *       (e.g. {@code :server:server-vertx} under Quarkus).</li>
 * </ul>
 */
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

    /**
     * Construct a resolver from a caller-supplied, precision-sorted list of
     * {@link ServerProtocol} instances. Use this overload when the resolver's
     * own static {@link ServiceLoader} cannot see the runtime protocol jars
     * (e.g. under {@code QuarkusClassLoader}); the caller is responsible for
     * loading the providers and sorting them by
     * {@link ServerProtocolProvider#precision()} ascending.
     */
    public ProtocolResolver(ServiceMatcher serviceMatcher, List<? extends ServerProtocol> serverProtocols) {
        if (serverProtocols.isEmpty()) {
            // A resolver constructed with no protocols would silently
            // turn every request into Optional.empty (no-claim). Catch
            // the misconfiguration at construction time instead.
            throw new IllegalArgumentException("serverProtocols must not be empty");
        }
        this.serverProtocolHandlers = List.copyOf(serverProtocols);
        this.serviceMatcher = serviceMatcher;
    }

    public ServiceProtocolResolutionResult resolve(ServiceProtocolResolutionRequest request) {
        return resolveOrEmpty(request).orElseThrow(() -> UnknownOperationException.builder()
                .message("No matching operations found for request")
                .build());
    }

    /**
     * Resolve the request as in {@link #resolve}, but distinguish <em>no
     * protocol claimed it</em> (returns {@link Optional#empty()}) from
     * <em>a protocol claimed it but rejected the input</em> (throws
     * {@link UnknownOperationException}).
     *
     * <p>The distinction relies on the
     * {@link ServerProtocol#resolveOperation} contract: returning
     * {@code null} means "this request is not mine"; throwing
     * {@link UnknownOperationException} means "this request is mine but
     * malformed". Per the Smithy 2.0 Wire-protocol-selection guide, a
     * malformed claim is terminal — later protocols are not consulted —
     * so the throw is propagated rather than swallowed.
     *
     * <p>Transports sharing a router with non-Smithy handlers use this
     * method so that unrecognised requests can be forwarded (e.g.
     * {@code ctx.next()}) instead of returning a misleading 404.
     */
    public Optional<ServiceProtocolResolutionResult> resolveOrEmpty(ServiceProtocolResolutionRequest request) {
        var candidates = serviceMatcher.getCandidateServices(request);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        for (ServerProtocol protocol : serverProtocolHandlers) {
            var resolutionResult = protocol.resolveOperation(request, candidates);
            if (resolutionResult != null) {
                return Optional.of(resolutionResult);
            }
        }
        return Optional.empty();
    }
}
