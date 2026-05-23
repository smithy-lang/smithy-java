/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.vertx;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Route;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.ErrorHandlingOrchestrator;
import software.amazon.smithy.java.server.core.HandlerAssembler;
import software.amazon.smithy.java.server.core.HttpJob;
import software.amazon.smithy.java.server.core.HttpRequest;
import software.amazon.smithy.java.server.core.HttpResponse;
import software.amazon.smithy.java.server.core.HttpResponseSerializer;
import software.amazon.smithy.java.server.core.HttpResponseSerializer.SerializedResponse;
import software.amazon.smithy.java.server.core.OrchestratorGroup;
import software.amazon.smithy.java.server.core.ProtocolResolver;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServiceMatcher;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.java.server.core.SingleThreadOrchestrator;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A Smithy server that runs on top of Vert.x. Implements
 * {@link Handler}{@code <}{@link RoutingContext}{@code >} so the
 * Quarkus integration can mount it on Quarkus's main {@link Router} as
 * a regular Vert.x route handler.
 *
 * <p>The server resolves the protocol per request via
 * {@link ProtocolResolver}, iterating a precision-ordered list of
 * {@link ServerProtocol}s. This is the spec-literal embodiment of
 * <a href="https://smithy.io/2.0/guides/wire-protocol-selection.html">Smithy 2.0
 * Wire protocol selection</a>.
 *
 * <p>Resolution is tri-state:
 * <ul>
 *   <li><b>claimed</b> — a protocol returned a result. The server
 *       enqueues an {@link HttpJob} on its orchestrator and writes the
 *       response on completion.</li>
 *   <li><b>no-claim</b> — every protocol returned {@code null}. The
 *       server calls {@code ctx.next()} so the request can be served by
 *       a sibling Vert.x handler (e.g. a Quarkus REST endpoint).</li>
 *   <li><b>claim-and-reject</b> — a protocol threw
 *       {@link UnknownOperationException} (e.g. rpcv2 with a malformed
 *       URI). The server returns 404 directly; the request is not handed
 *       to other Vert.x handlers because it would be misinterpreted.</li>
 * </ul>
 *
 * <p>The server accepts both HTTP/1.1 and HTTP/2 traffic; wire-version
 * negotiation is delegated entirely to the Vert.x server hosting the
 * router (so that ALPN, h2c upgrade, and TLS choices live with the
 * server, not with us). Response framing flows through Vert.x's
 * {@link HttpServerResponse}, preserving the version Vert.x parsed in.
 *
 * <p>Body buffering is the caller's responsibility — install Vert.x's
 * {@code BodyHandler} upstream of this handler. {@code @streaming Blob}
 * operations are not currently supported.
 *
 * <p><b>Lifecycle.</b> The caller owns the {@link io.vertx.ext.web.Route}
 * returned by Vert.x; remove it via {@code Route.remove()}. The server
 * owns the orchestrator pool; drain it via {@link #shutdown()}.
 */
@SmithyUnstableApi
public final class SmithyVertxServer implements Handler<RoutingContext> {

    private static final InternalLogger LOG = InternalLogger.getLogger(SmithyVertxServer.class);

    private final List<Service> services;
    private final ServerOptions options;
    private final ProtocolResolver resolver;
    private final OrchestratorGroup orchestrator;
    private volatile CompletableFuture<Void> shutdownFuture;

    private SmithyVertxServer(List<Service> services, ServerOptions options, List<ServerProtocol> protocols) {
        this.services = List.copyOf(services);
        this.options = options;
        // A single trivial Route encompasses every service (no
        // host/port/protocol/prefix discrimination), so
        // ServiceMatcher.getCandidateServices returns every service for
        // every request — mirroring server-netty's flat routing.
        var matcher = new ServiceMatcher(List.of(
                Route.builder()
                        .pathPrefix("/")
                        .services(this.services)
                        .build()));
        this.resolver = new ProtocolResolver(matcher, protocols);
        this.orchestrator = newOrchestrator();
        logMountSummary(protocols);
    }

    /**
     * Construct a server with default options.
     *
     * @param services the Smithy services to serve. Must be non-empty.
     * @param protocols precision-sorted list of {@link ServerProtocol}s
     *     the caller has loaded (typically via {@code ServiceLoader}).
     */
    public static SmithyVertxServer create(List<Service> services, List<ServerProtocol> protocols) {
        return create(services, protocols, ServerOptions.defaults());
    }

    /**
     * Construct a server with custom options.
     *
     * @param services the Smithy services to serve. Must be non-empty.
     * @param protocols precision-sorted list of {@link ServerProtocol}s
     *     the caller has loaded (typically via {@code ServiceLoader}).
     * @param options tunable parameters.
     */
    public static SmithyVertxServer create(
            List<Service> services,
            List<ServerProtocol> protocols,
            ServerOptions options
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(protocols, "protocols");
        Objects.requireNonNull(options, "options");
        if (services.isEmpty()) {
            throw new IllegalArgumentException(
                    "SmithyVertxServer requires at least one Service. "
                            + "Add @Produces Service beans (Quarkus) or pass them directly.");
        }
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException(
                    "SmithyVertxServer requires at least one ServerProtocol. "
                            + "Load providers via ServiceLoader, sort by precision(), and pass them in.");
        }
        return new SmithyVertxServer(services, options, protocols);
    }

    @Override
    public void handle(RoutingContext rc) {
        try {
            handleInternal(rc);
        } catch (Throwable t) {
            // Defensive top-level catch: a buggy protocol or a header
            // wrapper that throws on a malformed input must not leave
            // the Vert.x handler chain in an undefined state. Log and
            // 500 — the user's request fails, the server keeps running.
            LOG.error("Unhandled exception in Smithy server dispatch", t);
            if (!rc.response().headWritten()) {
                rc.response().setStatusCode(500).end();
            }
        }
    }

    /**
     * Best-effort drain of the orchestrator. Subsequent calls return
     * the same future (idempotent).
     *
     * <p>The returned future is the raw orchestrator drain future —
     * it carries no built-in deadline and may remain pending if a
     * worker is stuck. Callers MUST impose their own bound, e.g. via
     * {@code shutdown().get(grace, TimeUnit.MILLISECONDS)} or
     * {@code shutdown().orTimeout(...)}. The Quarkus recorder applies
     * {@code quarkus.smithy.server.shutdown-grace} this way.
     *
     * <p><b>Caveat:</b> the underlying {@link OrchestratorGroup}'s
     * shutdown semantics are inherited from
     * {@code SingleThreadOrchestrator.shutdown()}, which currently
     * resolves immediately and relies on the worker being a daemon
     * thread that stops with the JVM. This API contract is expected to
     * tighten when {@code SingleThreadOrchestrator} implements proper
     * drain semantics.
     */
    public synchronized CompletableFuture<Void> shutdown() {
        if (shutdownFuture == null) {
            shutdownFuture = orchestrator.shutdown();
        }
        return shutdownFuture;
    }

    private void handleInternal(RoutingContext rc) {
        Buffer body = rc.body() == null ? null : rc.body().buffer();
        byte[] bytes = body == null ? new byte[0] : body.getBytes();

        URI uri = parseRequestUri(rc, options.pathPrefix());
        var requestHeaders = new VertxRequestHeaders(rc.request().headers());

        // Capture the Vert.x context now (request-side, on the event
        // loop) so the writeResponse callback — which runs on the
        // orchestrator's worker thread — can hand the response writes
        // back to the event loop. Vert.x's HttpServerResponse contract
        // is that mutations must run on the request's Context.
        Context vertxContext = Vertx.currentContext();

        dispatch(requestHeaders, uri, rc.request().method().name(), bytes)
                .whenComplete((job, t) -> handleCompletion(rc, vertxContext, job, t));
    }

    private CompletableFuture<HttpJob> dispatch(
            HttpHeaders requestHeaders,
            URI uri,
            String method,
            byte[] body
    ) {
        var smithyRequest = new HttpRequest(requestHeaders, uri, method);
        smithyRequest.setDataStream(DataStream.ofBytes(body, requestHeaders.contentType()));

        var resolutionRequest = new ServiceProtocolResolutionRequest(
                uri,
                requestHeaders,
                smithyRequest.context(),
                method);

        Optional<ServiceProtocolResolutionResult> resolved;
        try {
            resolved = resolver.resolveOrEmpty(resolutionRequest);
        } catch (UnknownOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (resolved.isEmpty()) {
            return CompletableFuture.failedFuture(new NoMatchingProtocol());
        }
        var result = resolved.get();

        var smithyResponse = new HttpResponse(HttpHeaders.ofModifiable());

        @SuppressWarnings({"rawtypes", "unchecked"})
        HttpJob job = new HttpJob(
                (Operation) result.operation(),
                result.protocol(),
                smithyRequest,
                smithyResponse);

        return orchestrator.enqueue(job).thenApply(v -> job);
    }

    private static void handleCompletion(RoutingContext rc, Context vertxContext, HttpJob job, Throwable t) {
        Throwable cause = unwrap(t);
        // All three branches mutate the routing context (next() may run
        // user-installed handlers that touch the response;
        // setStatusCode/end mutate response state directly), so each
        // must run on the request's Vert.x Context per
        // HttpServerResponse's threading contract.
        if (cause instanceof NoMatchingProtocol) {
            runOnContext(vertxContext, rc::next);
            return;
        }
        if (cause instanceof UnknownOperationException) {
            runOnContext(vertxContext, () -> {
                if (!rc.response().headWritten()) {
                    rc.response().setStatusCode(404).end();
                }
            });
            return;
        }
        // claim-and-resolve (success) OR an orchestrator-side throw
        // (cause != null). writeResponse handles both shapes.
        runOnContext(vertxContext, () -> writeResponse(rc, job, cause));
    }

    private static Throwable unwrap(Throwable t) {
        // The orchestrator.enqueue(...).thenApply(...) chain wraps user
        // exceptions in a single CompletionException; peel it once so
        // callers' instanceof checks see the real type.
        return (t instanceof CompletionException ce && ce.getCause() != null) ? ce.getCause() : t;
    }

    private static void runOnContext(Context vertxContext, Runnable r) {
        // vertxContext is captured in handleInternal via Vertx.currentContext(),
        // which is non-null when called from a Vert.x route handler — the
        // only path that reaches this server.
        vertxContext.runOnContext(v -> r.run());
    }

    private static URI parseRequestUri(RoutingContext rc, String pathPrefix) {
        // Vert.x's request().uri() returns the path + query as written
        // by the client. We reconstruct an absolute-ish URI so Smithy's
        // protocol layer sees scheme/host/port consistently with the
        // Netty path. The path prefix the server applied at mount time
        // is stripped here so the protocol's matcher (which uses the
        // model's raw `@http(uri:...)` traits) sees the operation's
        // canonical path.
        var req = rc.request();
        String stripped = stripPrefix(req.uri(), pathPrefix);
        try {
            String scheme = req.isSSL() ? "https" : "http";
            String authority = req.host() == null ? "localhost" : req.host();
            return new URI(scheme + "://" + authority + stripped);
        } catch (URISyntaxException e) {
            return URI.create(stripped);
        }
    }

    private static String stripPrefix(String rawUri, String pathPrefix) {
        if (pathPrefix.isEmpty() || rawUri == null || rawUri.isEmpty()) {
            return rawUri;
        }
        // pathPrefix is normalized to begin with "/" and not end with "/".
        if (rawUri.startsWith(pathPrefix)) {
            String tail = rawUri.substring(pathPrefix.length());
            return tail.isEmpty() ? "/" : tail;
        }
        return rawUri;
    }

    private static void writeResponse(RoutingContext rc, HttpJob job, Throwable failure) {
        var resp = rc.response();
        if (resp.ended()) {
            return;
        }
        if (failure != null) {
            LOG.error("Smithy operation {} failed; returning 500",
                    job == null ? "<unknown>" : job.operation().name(),
                    failure);
        }
        try {
            SerializedResponse sr = HttpResponseSerializer.from(job, failure);
            resp.setStatusCode(sr.statusCode());
            for (var entry : sr.headers().map().entrySet()) {
                for (String value : entry.getValue()) {
                    resp.headers().add(entry.getKey(), value);
                }
            }
            if (sr.body() == null) {
                resp.end();
            } else {
                // Vert.x's Buffer constructors only accept byte[];
                // materialise the DataStream's ByteBuffer once.
                var bb = sr.body().asByteBuffer();
                byte[] bytes = new byte[bb.remaining()];
                bb.get(bytes);
                resp.end(Buffer.buffer(bytes));
            }
        } catch (Throwable e) {
            LOG.error("Failed to write Smithy response for operation {}",
                    job == null ? "<unknown>" : job.operation().name(),
                    e);
            if (!resp.headWritten()) {
                resp.setStatusCode(500);
                resp.end();
            }
        }
    }

    private OrchestratorGroup newOrchestrator() {
        var handlers = new HandlerAssembler().assembleHandlers(services);
        return new OrchestratorGroup(
                options.workerCount(),
                () -> new ErrorHandlingOrchestrator(new SingleThreadOrchestrator(handlers)),
                OrchestratorGroup.Strategy.roundRobin());
    }

    private void logMountSummary(List<ServerProtocol> protocols) {
        int operationCount = 0;
        for (Service s : services) {
            operationCount += s.getAllOperations().size();
        }
        var protocolIds = new ArrayList<String>(protocols.size());
        for (ServerProtocol p : protocols) {
            protocolIds.add(p.getProtocolId().toString());
        }
        LOG.info(
                "Smithy server constructed with {} service(s), {} operation(s); protocols (precision order): {}",
                services.size(),
                operationCount,
                protocolIds);
    }

    /**
     * Marker exception completed when no protocol claimed a request.
     * Translated by {@link #handleCompletion} to {@code ctx.next()}.
     */
    private static final class NoMatchingProtocol extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NoMatchingProtocol() {
            super("no protocol claimed the request");
        }
    }
}
