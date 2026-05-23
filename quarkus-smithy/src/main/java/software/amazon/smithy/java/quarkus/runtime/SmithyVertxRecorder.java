/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.quarkus.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.TypeLiteral;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServerProtocolProvider;
import software.amazon.smithy.java.server.vertx.ServerOptions;
import software.amazon.smithy.java.server.vertx.SmithyVertxServer;

/**
 * Quarkus {@link Recorder} that mounts every CDI-discovered
 * {@link Service} bean on Quarkus's main Vert.x {@link Router} via
 * {@link SmithyVertxServer}.
 *
 * <p>The recorder's job is:
 * <ol>
 *   <li>Collect {@code Service} beans from Arc.</li>
 *   <li>Load {@link ServerProtocol} providers via {@link ServiceLoader}
 *       on the thread-context classloader (Quarkus's
 *       {@code QuarkusClassLoader}, which sees runtime jars) plus the
 *       recorder's own classloader. Sort by precision.</li>
 *   <li>Translate {@link SmithyServerConfig} into {@link ServerOptions}
 *       and construct a {@link SmithyVertxServer}.</li>
 *   <li>Mount the server on Quarkus's main Vert.x {@link Router} as a
 *       single catch-all route under the configured prefix.</li>
 *   <li>Wire route removal and orchestrator drain into Quarkus's
 *       shutdown sequence so dev-mode hot reload removes the route
 *       cleanly and graceful shutdown drains workers.</li>
 * </ol>
 *
 * <p>Run-time {@link SmithyServerConfig} is injected via the recorder
 * constructor as a {@link RuntimeValue}; build-step methods cannot
 * consume run-time config directly (Quarkus enforces that distinction).
 */
@Recorder
public class SmithyVertxRecorder {

    private static final InternalLogger LOG = InternalLogger.getLogger(SmithyVertxRecorder.class);

    private final RuntimeValue<SmithyServerConfig> config;

    public SmithyVertxRecorder(RuntimeValue<SmithyServerConfig> config) {
        this.config = config;
    }

    public void mount(
            RuntimeValue<Router> mainRouter,
            ShutdownContext shutdown
    ) {

        // Discover @Produces Service beans via CDI. Multi-Service
        // composition is supported.
        var instance = Arc.container()
                .select(
                        new TypeLiteral<Service>() {},
                        Any.Literal.INSTANCE);

        List<Service> services = new ArrayList<>();
        for (Service service : instance) {
            services.add(service);
            LOG.info(
                    "Discovered Smithy Service '{}' with {} operation(s)",
                    service.schema().id(),
                    service.getAllOperations().size());
        }

        if (services.isEmpty()) {
            // Apps that depend on quarkus-smithy purely for codegen
            // produce no `@Produces Service` beans. The server
            // requires at least one; short-circuit so the extension
            // is silent in that case. Codegen still ran earlier in
            // the build pipeline.
            LOG.info(
                    "No @Produces Service beans found. Skipping the Vert.x "
                            + "server mount; codegen-only apps will not see any "
                            + "Smithy operations on the HTTP router.");
            return;
        }

        List<ServerProtocol> protocols = loadServerProtocols(services);
        if (protocols.isEmpty()) {
            throw new IllegalStateException(
                    "No ServerProtocol implementations found on the classpath. "
                            + "Add the protocol module(s) your services declare (e.g. "
                            + "aws-server-restjson, server-rpcv2-cbor) to the runtime "
                            + "classpath.");
        }

        SmithyServerConfig cfg = config.getValue();
        var optionsBuilder = ServerOptions.builder()
                .pathPrefix(cfg.pathPrefix().orElse(""));
        cfg.workers().ifPresent(optionsBuilder::workerCount);
        ServerOptions options = optionsBuilder.build();

        SmithyVertxServer server = SmithyVertxServer.create(services, protocols, options);

        // Mount as a single catch-all route under the configured prefix.
        Router router = mainRouter.getValue();
        String mountPath = options.pathPrefix().isEmpty()
                ? null
                : options.pathPrefix() + "/*";
        Route route = (mountPath == null ? router.route() : router.route(mountPath))
                .handler(BodyHandler.create())
                .handler(server);

        LOG.info(
                "Smithy mounted at {} with {} service(s)",
                options.pathPrefix().isEmpty() ? "/*" : options.pathPrefix() + "/*",
                services.size());

        // Hot reload + ordered shutdown. Quarkus's ShutdownContext runs
        // tasks in *reverse* registration order (LIFO), so the last task
        // registered runs first. We want:
        //   1) route.remove() — stop accepting new requests
        //   2) server.shutdown() — drain the orchestrator pool
        // So register server.shutdown() FIRST (runs second) and
        // route.remove() SECOND (runs first).
        shutdown.addShutdownTask(() -> {
            try {
                server.shutdown().get(cfg.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                LOG.warn("Smithy server shutdown timed out after {}", cfg.shutdownGrace());
            } catch (Exception e) {
                LOG.warn("Error during Smithy server shutdown", e);
            }
        });
        shutdown.addShutdownTask(route::remove);
    }

    /**
     * Discover {@link ServerProtocolProvider}s on the runtime classpath
     * and instantiate each one with the recorder's services. The result
     * is sorted ascending by {@link ServerProtocolProvider#precision()},
     * so the head of the list is the highest-precision protocol per the
     * AWS service-protocol order in the Smithy 2.0 Wire-protocol-selection
     * guide.
     *
     * <p>Resolves providers from both the thread-context classloader
     * (Quarkus's {@code QuarkusClassLoader} at recorder time, which sees
     * every runtime jar) and the recorder's own loader, deduped by
     * provider class. Required because
     * {@code ProtocolResolver}'s static SPI cache (keyed on its own
     * classloader) does not see runtime protocol jars under Quarkus's
     * partitioned classloader hierarchy.
     */
    private static List<ServerProtocol> loadServerProtocols(List<Service> services) {
        // Single-thread safe: scoped to one mount() invocation, never published.
        List<ServerProtocolProvider> providers = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            collectProviders(tccl, providers, seen);
        }
        ClassLoader own = SmithyVertxRecorder.class.getClassLoader();
        if (own != tccl) {
            collectProviders(own, providers, seen);
        }
        providers.sort(Comparator.comparingInt(ServerProtocolProvider::precision));
        List<ServerProtocol> out = new ArrayList<>(providers.size());
        for (ServerProtocolProvider provider : providers) {
            out.add(provider.provideProtocolHandler(services));
        }
        return Collections.unmodifiableList(out);
    }

    private static void collectProviders(
            ClassLoader cl,
            List<ServerProtocolProvider> providers,
            Set<Class<?>> seen
    ) {
        for (var provider : ServiceLoader.load(ServerProtocolProvider.class, cl)) {
            if (seen.add(provider.getClass())) {
                providers.add(provider);
            }
        }
    }
}
