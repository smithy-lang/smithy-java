/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * A dev/eval-only {@link ClientPlugin} that attaches the {@link InspectorInterceptor}, giving an
 * MCP-connected agent live observe/inject/breakpoint access to the client.
 *
 * <p><strong>Opt-in.</strong> This plugin does nothing unless explicitly enabled, so it is safe to
 * leave on a classpath. It activates when either:
 * <ul>
 *   <li>the system property {@code smithy.inspector} is set (to any value), or</li>
 *   <li>the environment variable {@code SMITHY_INSPECTOR} is set, or</li>
 *   <li>it is constructed with an explicit {@link InspectorState}.</li>
 * </ul>
 *
 * <p>This is deliberately <em>not</em> an {@code AutoClientPlugin}: attach it explicitly, or add a
 * thin auto-plugin subclass in your eval harness that reads the flag. Keeping the auto-discovery
 * decision out of this artifact avoids ever silently instrumenting a production client.
 *
 * <p>Usage:
 * {@snippet lang = "java":
 *     var state = new InspectorState();
 *     var client = MyClient.builder()
 *         .addPlugin(new InspectorPlugin(state))
 *         .build();
 *     // Expose `state` over MCP:
 *     InspectorMcpLauncher.stdio(state).start();
 * }
 */
public final class InspectorPlugin implements ClientPlugin {

    /** System property that enables the flag-gated no-arg constructor. */
    public static final String ENABLE_PROPERTY = "smithy.inspector";
    /** Environment variable that enables the flag-gated no-arg constructor. */
    public static final String ENABLE_ENV = "SMITHY_INSPECTOR";

    private final InspectorState state;
    private final boolean enabled;

    /**
     * Flag-gated constructor: the plugin only instruments the client if {@link #ENABLE_PROPERTY} or
     * {@link #ENABLE_ENV} is set. Uses {@link InspectorRegistry#shared()} so the same state is
     * reachable by an MCP launcher elsewhere in the process.
     */
    public InspectorPlugin() {
        this.enabled = System.getProperty(ENABLE_PROPERTY) != null || System.getenv(ENABLE_ENV) != null;
        this.state = enabled ? InspectorRegistry.shared() : null;
    }

    /** Always-on constructor using an explicit state instance. */
    public InspectorPlugin(InspectorState state) {
        this.state = state;
        this.enabled = true;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (!enabled) {
            return;
        }
        config.addInterceptor(new InspectorInterceptor(state));
    }

    /**
     * Run in a late phase so the inspector's interceptor is ordered <em>after</em> the framework's
     * default plugins (which run in {@link Phase#FIRST}), notably the retry-classification
     * interceptors from {@code ApplyModelRetryInfoPlugin}/{@code ApplyHttpRetryInfoPlugin}.
     *
     * <p>This matters because the {@code modifyBeforeAttemptCompletion} hook is invoked across
     * interceptors with no per-interceptor error isolation: the first interceptor that lets an
     * error propagate through that hook stops the chain, so an observer ordered <em>before</em> the
     * retry classifier would silently suppress retry classification. Pinning a late phase keeps the
     * inspector a pure observer of retry behavior rather than an accidental participant in it.
     */
    @Override
    public Phase getPluginPhase() {
        return Phase.LAST;
    }

    /** The state this plugin records into, or null if disabled. */
    public InspectorState state() {
        return state;
    }
}
