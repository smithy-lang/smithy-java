/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.vertx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServerProtocolProvider;

/**
 * Integration tests for {@link SmithyVertxServer}. Each test spins up a
 * real Vert.x HTTP server, mounts the Smithy server on its router as a
 * Vert.x {@link Route}, and sends real HTTP requests via {@link WebClient}.
 */
@ExtendWith(VertxExtension.class)
class SmithyVertxServerTest {

    private Vertx vertx;
    private HttpServer httpServer;
    private WebClient client;
    private Router router;
    private int port;

    private SmithyVertxServer smithyServer;
    private Route smithyRoute;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext ctx) throws InterruptedException {
        this.vertx = vertx;
        this.router = Router.router(vertx);
        this.httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        httpServer.requestHandler(router)
                .listen()
                .onComplete(ctx.succeedingThenComplete());
        ctx.awaitCompletion(5, TimeUnit.SECONDS);
        this.port = httpServer.actualPort();
        this.client = WebClient.create(vertx);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (smithyRoute != null) {
            smithyRoute.remove();
            smithyRoute = null;
        }
        if (smithyServer != null) {
            smithyServer.shutdown().get(5, TimeUnit.SECONDS);
            smithyServer = null;
        }
        if (client != null) {
            client.close();
        }
        if (httpServer != null) {
            httpServer.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Mount a Smithy server on this test's router with default options.
     * Stashes the constructed {@link SmithyVertxServer} and {@link Route}
     * for {@link #tearDown()}.
     */
    private void mount(List<Service> services) {
        mount(services, ServerOptions.defaults(), this.router);
    }

    private void mount(List<Service> services, ServerOptions options) {
        mount(services, options, this.router);
    }

    private void mount(List<Service> services, ServerOptions options, Router target) {
        this.smithyServer = SmithyVertxServer.create(services, loadProtocols(services), options);
        String mountPath = options.pathPrefix().isEmpty()
                ? null
                : options.pathPrefix() + "/*";
        var route = (mountPath == null ? target.route() : target.route(mountPath))
                .handler(BodyHandler.create())
                .handler(smithyServer);
        if (target == this.router) {
            this.smithyRoute = route;
        }
    }

    private static List<ServerProtocol> loadProtocols(List<Service> services) {
        var providers = new ArrayList<ServerProtocolProvider>();
        for (var p : ServiceLoader.load(
                ServerProtocolProvider.class,
                SmithyVertxServerTest.class.getClassLoader())) {
            providers.add(p);
        }
        providers.sort(Comparator.comparingInt(ServerProtocolProvider::precision));
        var protocols = new ArrayList<ServerProtocol>(providers.size());
        for (var p : providers) {
            // Some protocols (notably restJson1) build their per-service
            // matcher map at construction time and need the real service
            // list to route by @http(uri) traits.
            protocols.add(p.provideProtocolHandler(services));
        }
        return protocols;
    }

    @Test
    void getMenuRespondsWithSerializedOutput(VertxTestContext ctx) {
        var menu = MenuFixture.menuService();
        mount(List.of(menu));

        client.get(port, "localhost", "/menu")
                .send()
                .onComplete(ctx.succeeding(resp -> ctx.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    // The operation must have been invoked end-to-end,
                    // not just the route mounted.
                    assertThat(menu.lastInvoked.get()).isEqualTo("GetMenu");
                    ctx.completeNow();
                })));
    }

    @Test
    void restJson1PathParametersAndMethodsRouteCorrectly(VertxTestContext ctx) {
        var menu = MenuFixture.menuService();
        mount(List.of(menu));

        // GET /order/abc must route to GetOrder (the @http(uri:"/order/{id}")
        // operation, translated to Vert.x's /order/:id).
        client.get(port, "localhost", "/order/abc")
                .send()
                .onComplete(ctx.succeeding(resp1 -> ctx.verify(() -> {
                    assertThat(resp1.statusCode()).isEqualTo(200);
                    assertThat(menu.lastInvoked.get()).isEqualTo("GetOrder");

                    // PUT /order must route to PutOrder (different method,
                    // overlapping path prefix with the labeled route).
                    client.put(port, "localhost", "/order")
                            .sendBuffer(Buffer.buffer("{}"))
                            .onComplete(ctx.succeeding(resp2 -> ctx.verify(() -> {
                                assertThat(resp2.statusCode()).isEqualTo(200);
                                assertThat(menu.lastInvoked.get()).isEqualTo("PutOrder");
                                ctx.completeNow();
                            })));
                })));
    }

    @Test
    void rpcv2CborRequiresSmithyProtocolHeader(VertxTestContext ctx) {
        var ping = PingFixture.pingService();
        mount(List.of(ping));

        // Request WITHOUT smithy-protocol header: rpcv2-cbor's
        // resolveOperation returns null (= "not mine"), so the server
        // calls ctx.next(). With no other route installed on this
        // test's router, Vert.x produces its default 404 — the
        // observable status is still 404, but the source of the 404
        // changed (Vert.x default-route vs Smithy explicit reject).
        client.post(port, "localhost", "/service/Ping/operation/Ping")
                .sendBuffer(Buffer.buffer(new byte[0]))
                .onComplete(ctx.succeeding(resp1 -> ctx.verify(() -> {
                    assertThat(resp1.statusCode()).isEqualTo(404);

                    // Request WITH the correct smithy-protocol header → 200
                    var emptyCborMap = Buffer.buffer(new byte[] {(byte) 0xa0});
                    client.post(port, "localhost", "/service/Ping/operation/Ping")
                            .putHeader("smithy-protocol", "rpc-v2-cbor")
                            .putHeader("content-type", "application/cbor")
                            .sendBuffer(emptyCborMap)
                            .onComplete(ctx.succeeding(resp2 -> ctx.verify(() -> {
                                assertThat(resp2.statusCode()).isEqualTo(200);
                                assertThat(ping.lastInvoked.get()).isEqualTo("Ping");
                                ctx.completeNow();
                            })));
                })));
    }

    @Test
    void rpcv2CborWithHeaderButMalformedUriReturns404(VertxTestContext ctx) {
        // Claim-and-reject branch: a request with the rpc-v2-cbor
        // header is unambiguously the rpcv2-cbor protocol's, so a
        // malformed URI must NOT fall through via ctx.next() to a
        // sibling Vert.x handler — it must 404 from the server directly.
        var ping = PingFixture.pingService();
        mount(List.of(ping));

        // Install a sentinel user route at the same path that would
        // be reached if the server incorrectly fell through.
        router.post("/service/Ping/operation/")
                .handler(rc -> rc.response()
                        .setStatusCode(200)
                        .end("should-not-reach"));

        client.post(port, "localhost", "/service/Ping/operation/")
                .putHeader("smithy-protocol", "rpc-v2-cbor")
                .putHeader("content-type", "application/cbor")
                .sendBuffer(Buffer.buffer(new byte[] {(byte) 0xa0}))
                .onComplete(ctx.succeeding(resp -> ctx.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(404);
                    assertThat(resp.bodyAsString()).isNullOrEmpty();
                    ctx.completeNow();
                })));
    }

    @Test
    void multipleServicesAcrossProtocolsCoexist(VertxTestContext ctx) {
        var menu = MenuFixture.menuService();
        var ping = PingFixture.pingService();
        mount(List.of(menu, ping));

        client.get(port, "localhost", "/menu")
                .send()
                .onComplete(ctx.succeeding(resp1 -> ctx.verify(() -> {
                    assertThat(resp1.statusCode()).isEqualTo(200);

                    client.post(port, "localhost", "/service/Ping/operation/Ping")
                            .putHeader("smithy-protocol", "rpc-v2-cbor")
                            .putHeader("content-type", "application/cbor")
                            .sendBuffer(Buffer.buffer(new byte[] {(byte) 0xa0}))
                            .onComplete(ctx.succeeding(resp2 -> ctx.verify(() -> {
                                assertThat(resp2.statusCode()).isEqualTo(200);
                                assertThat(ping.lastInvoked.get()).isEqualTo("Ping");
                                ctx.completeNow();
                            })));
                })));
    }

    @Test
    void http2RequestGetsHttp2Response(VertxTestContext ctx) throws Exception {
        // Bring up a *separate* HTTP server with HTTP/2 (h2c) enabled,
        // so we can verify the server does not hard-code HTTP/1.1 in
        // its response framing. The setUp() server is HTTP/1-only.
        var h2cRouter = Router.router(vertx);
        var menu = MenuFixture.menuService();
        mount(List.of(menu), ServerOptions.defaults(), h2cRouter);

        var h2cServer = vertx.createHttpServer(new HttpServerOptions()
                .setPort(0)
                .setUseAlpn(false)
                .setHttp2ClearTextEnabled(true)
                .addEnabledSecureTransportProtocol("TLSv1.3"));
        var listenFuture = h2cServer.requestHandler(h2cRouter).listen();
        listenFuture.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        int h2cPort = h2cServer.actualPort();

        var h2cClient = WebClient.create(vertx,
                new WebClientOptions()
                        .setProtocolVersion(io.vertx.core.http.HttpVersion.HTTP_2)
                        .setHttp2ClearTextUpgrade(true));

        h2cClient.get(h2cPort, "localhost", "/menu")
                .send()
                .onComplete(ctx.succeeding(resp -> ctx.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.version()).isEqualTo(io.vertx.core.http.HttpVersion.HTTP_2);
                    h2cClient.close();
                    h2cServer.close()
                            .toCompletionStage()
                            .toCompletableFuture()
                            .whenComplete((v, t) -> ctx.completeNow());
                })));
    }

    @Test
    void routeRemovalUnmountsTheServer(VertxTestContext ctx) {
        var menu = MenuFixture.menuService();
        mount(List.of(menu));
        // Before remove, /menu hits the server.
        client.get(port, "localhost", "/menu")
                .send()
                .onComplete(ctx.succeeding(resp1 -> ctx.verify(() -> {
                    assertThat(resp1.statusCode()).isEqualTo(200);

                    smithyRoute.remove();
                    smithyRoute = null; // tearDown won't try to remove it again
                    // After remove, /menu falls through; no other route
                    // installed, so Vert.x returns 404.
                    client.get(port, "localhost", "/menu")
                            .send()
                            .onComplete(ctx.succeeding(resp2 -> ctx.verify(() -> {
                                assertThat(resp2.statusCode()).isEqualTo(404);
                                ctx.completeNow();
                            })));
                })));
    }

    @Test
    void shutdownReturnsCompletableFutureAndIsIdempotent() throws Exception {
        var menu = MenuFixture.menuService();
        mount(List.of(menu));

        var f1 = smithyServer.shutdown();
        f1.get(15, TimeUnit.SECONDS);
        assertThat(f1).isDone();

        // Calling shutdown again returns the *same* future (idempotent).
        var f2 = smithyServer.shutdown();
        assertThat(f2).isSameAs(f1);

        smithyServer = null;
    }

    @Test
    void pathPrefixOptionPrependsAllOperationRoutes(VertxTestContext ctx) {
        var menu = MenuFixture.menuService();
        var options = ServerOptions.builder().pathPrefix("/api").build();
        mount(List.of(menu), options);

        // Operation declared at @http(uri:"/menu") is now reachable at
        // /api/menu, NOT at /menu.
        client.get(port, "localhost", "/api/menu")
                .send()
                .onComplete(ctx.succeeding(resp1 -> ctx.verify(() -> {
                    assertThat(resp1.statusCode()).isEqualTo(200);
                    assertThat(menu.lastInvoked.get()).isEqualTo("GetMenu");

                    client.get(port, "localhost", "/menu")
                            .send()
                            .onComplete(ctx.succeeding(resp2 -> ctx.verify(() -> {
                                assertThat(resp2.statusCode()).isEqualTo(404);
                                ctx.completeNow();
                            })));
                })));
    }

    @Test
    void workerCountOptionAcceptsCustomSize() {
        var menu = MenuFixture.menuService();
        var options = ServerOptions.builder().workerCount(2).build();
        mount(List.of(menu), options);
    }

    @Test
    void idleServerShutdownReturnsPromptly() throws Exception {
        // The server's shutdown() returns the orchestrator's drain
        // future as-is; bounding it is the caller's responsibility
        // (the Quarkus recorder wraps with .get(grace, ms)). This test
        // catches the regression where an idle server's drain blocks
        // unboundedly — the actual property the server itself owns.
        var menu = MenuFixture.menuService();
        mount(List.of(menu));

        long start = System.nanoTime();
        smithyServer.shutdown().get(5, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(2_000L);
    }

    /**
     * Precision-ordering regression: every shipped
     * {@code ServerProtocolProvider.precision()} originally returned
     * {@code 0}, making the precision sort a no-op against classpath
     * order. This test loads every provider visible to the test runtime
     * and asserts they declare distinct, positive precisions.
     */
    @Test
    void shippedProvidersDeclareDistinctPositivePrecisions() {
        var providers = ServiceLoader
                .load(ServerProtocolProvider.class, SmithyVertxServerTest.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertThat(providers).as("expected at least rpcv2-cbor + restJson1 on the test classpath")
                .hasSizeGreaterThanOrEqualTo(2);
        var precisions = providers.stream()
                .map(ServerProtocolProvider::precision)
                .toList();
        assertThat(precisions)
                .as("Providers with the same precision tie and resolve in classpath order")
                .doesNotHaveDuplicates();
        assertThat(precisions).allSatisfy(p -> assertThat(p).isPositive());
    }

    /**
     * Forward-compat invariant: ServerOptions intentionally exposes no
     * interceptor field. Adding one later must remain a non-breaking,
     * additive change.
     */
    @Test
    void serverOptionsHasNoInterceptorField() {
        var declaredMethods = ServerOptions.Builder.class.getDeclaredMethods();
        for (var m : declaredMethods) {
            String name = m.getName().toLowerCase(Locale.ROOT);
            assertThat(name)
                    .as("ServerOptions.Builder method '%s' must not advertise an interceptor", m.getName())
                    .doesNotContain("interceptor");
        }
    }

    @Test
    void rpcv2EnvelopeReachesOperationOnAnyServiceByName(VertxTestContext ctx) {
        var menu = MenuFixture.menuService();
        mount(List.of(menu));

        client.post(port, "localhost", "/service/Menu/operation/GetMenu")
                .putHeader("smithy-protocol", "rpc-v2-cbor")
                .putHeader("content-type", "application/cbor")
                .sendBuffer(Buffer.buffer(new byte[] {(byte) 0xa0}))
                .onComplete(ctx.succeeding(resp -> ctx.verify(() -> {
                    assertThat(menu.lastInvoked.get()).isEqualTo("GetMenu");
                    ctx.completeNow();
                })));
    }

    @Test
    void zeroServicesAreRejected() {
        assertThatThrownBy(() -> SmithyVertxServer.create(List.of(), loadProtocols(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one Service");
    }

    @Test
    void zeroProtocolsAreRejected() {
        var menu = MenuFixture.menuService();
        assertThatThrownBy(() -> SmithyVertxServer.create(List.of(menu), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one ServerProtocol");
    }

    @Test
    void unrelatedPathFallsThroughToUserRoute(VertxTestContext ctx) {
        // Install a user route on the same router *before* mounting
        // the server — Vert.x route ordering is registration order.
        router.get("/admin/health")
                .handler(rc -> rc.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "text/plain")
                        .end("user-handler-ok"));

        Service menu = MenuFixture.menuService();
        mount(List.of(menu));

        client.get(port, "localhost", "/admin/health")
                .send()
                .onComplete(ctx.succeeding(resp -> ctx.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsString()).isEqualTo("user-handler-ok");
                    ctx.completeNow();
                })));
    }
}
