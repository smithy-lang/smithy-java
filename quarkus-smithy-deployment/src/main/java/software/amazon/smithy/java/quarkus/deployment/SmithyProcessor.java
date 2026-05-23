/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.quarkus.deployment;

import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.vertx.http.deployment.VertxWebRouterBuildItem;
import io.vertx.ext.web.Router;
import software.amazon.smithy.java.quarkus.runtime.SmithyVertxRecorder;
import software.amazon.smithy.java.server.Service;

/**
 * Quarkus {@code @BuildStep} processors for the {@code quarkus-smithy}
 * extension.
 *
 * <p>The runtime mounts every CDI-discovered {@link Service} bean on
 * Quarkus's main Vert.x {@code Router} via a {@code SmithyVertxServer}
 * from the upstream {@code :server:server-vertx} module. This class is
 * the Quarkus-specific glue.
 */
public final class SmithyProcessor {

    private static final String FEATURE = "smithy";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * {@link Service} beans must not be removed by Arc's bean-removal
     * pass even when the user does not {@code @Inject} them directly.
     * Marking the type unremovable keeps multi-{@code Service}
     * composition working without each user adding the
     * {@code @Unremovable} annotation themselves.
     */
    @BuildStep
    UnremovableBeanBuildItem keepServiceBeans() {
        return UnremovableBeanBuildItem.beanTypes(Service.class);
    }

    /**
     * Wire the recorder at {@code RUNTIME_INIT}. The recorder pulls
     * {@link Service} beans from Arc, translates the user's
     * {@link SmithyServerConfig} into
     * {@link software.amazon.smithy.java.server.vertx.ServerOptions},
     * and mounts a {@link software.amazon.smithy.java.server.vertx.SmithyVertxServer}
     * on the main Router.
     *
     * <p>{@code mainRouter} is the unprefixed router; when
     * {@code quarkus.http.root-path=/} (the default) Vert.x exposes it
     * as {@code httpRouter}, and {@code mainRouter} is {@code null}.
     * We fall back to {@code httpRouter} in that case so the server
     * always has a router to mount on.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void mountSmithyRoutes(
            SmithyVertxRecorder recorder,
            VertxWebRouterBuildItem routers,
            ShutdownContextBuildItem shutdown
    ) {
        RuntimeValue<Router> target = routers.getMainRouter();
        if (target == null) {
            target = routers.getHttpRouter();
        }
        recorder.mount(target, shutdown);
    }
}
