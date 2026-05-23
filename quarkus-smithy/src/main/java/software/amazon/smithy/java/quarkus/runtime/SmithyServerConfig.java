/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.quarkus.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;
// (Optional<String> pathPrefix avoids the empty-string converter trap
// that fires when @WithDefault("") is used with String.)

/**
 * Runtime configuration for the {@code quarkus-smithy} extension.
 *
 * <p>All knobs map onto
 * {@link software.amazon.smithy.java.server.vertx.ServerOptions}. The
 * extension does not configure listener-level concerns ({@code host},
 * {@code port}, TLS) because Smithy operations run on Quarkus's HTTP
 * server. Use the standard {@code quarkus.http.*} keys for those.
 */
@ConfigMapping(prefix = "quarkus.smithy.server")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface SmithyServerConfig {

    /**
     * Path prefix applied to the Smithy server's catch-all route. Absent
     * (default) means operations are reachable at the path their
     * {@code @http} trait or rpcv2 path syntax declares.
     *
     * <p>Set this when the user wants Smithy operations under a
     * sub-tree of the router (e.g., {@code /api/smithy}) so REST
     * endpoints at the root are not shadowed.
     */
    Optional<String> pathPrefix();

    /**
     * Worker pool size for the orchestrator group. Defaults to
     * {@code Runtime.getRuntime().availableProcessors() * 2}.
     */
    Optional<Integer> workers();

    /**
     * Bound applied by the recorder on
     * {@link software.amazon.smithy.java.server.vertx.SmithyVertxServer#shutdown()}.
     * Default: 10 seconds.
     */
    @WithDefault("10s")
    Duration shutdownGrace();
}
