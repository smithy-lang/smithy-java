/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.vertx;

import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Tunable parameters for {@link SmithyVertxServer}.
 *
 * <p>Two knobs:
 * <ul>
 *   <li>{@link #workerCount()} — orchestrator worker pool size. Default
 *       {@code Runtime.getRuntime().availableProcessors() * 2}.</li>
 *   <li>{@link #pathPrefix()} — applied to every operation's URI at
 *       routing time. Default {@code ""} (no prefix).</li>
 * </ul>
 *
 * <p>This class is intentionally narrow. Adding fields requires an ADR;
 * we want consumers to be able to read the surface in one screen and
 * understand exactly what the server does on their behalf.
 *
 * <p><b>Shutdown deadline.</b> {@link SmithyVertxServer#shutdown()}
 * does not impose a deadline; bounding the wait is the caller's
 * responsibility. The Quarkus recorder applies
 * {@code quarkus.smithy.server.shutdown-grace}; other callers can wrap
 * the returned future with {@code orTimeout(...)}.
 */
@SmithyUnstableApi
public final class ServerOptions {

    private static final ServerOptions DEFAULTS = builder().build();

    private final int workerCount;
    private final String pathPrefix;

    private ServerOptions(Builder b) {
        this.workerCount = b.workerCount;
        this.pathPrefix = b.pathPrefix;
    }

    public static ServerOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int workerCount() {
        return workerCount;
    }

    public String pathPrefix() {
        return pathPrefix;
    }

    public static final class Builder {
        private int workerCount = Runtime.getRuntime().availableProcessors() * 2;
        private String pathPrefix = "";

        private Builder() {}

        public Builder workerCount(int n) {
            if (n <= 0) {
                throw new IllegalArgumentException("workerCount must be > 0, got " + n);
            }
            this.workerCount = n;
            return this;
        }

        public Builder pathPrefix(String prefix) {
            Objects.requireNonNull(prefix, "pathPrefix");
            // Normalize: a non-empty prefix must start with "/" and must
            // not end with "/". Empty string means "no prefix".
            if (prefix.isEmpty()) {
                this.pathPrefix = "";
                return this;
            }
            String p = prefix.startsWith("/") ? prefix : "/" + prefix;
            if (p.length() > 1 && p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            this.pathPrefix = p;
            return this;
        }

        public ServerOptions build() {
            return new ServerOptions(this);
        }
    }
}
