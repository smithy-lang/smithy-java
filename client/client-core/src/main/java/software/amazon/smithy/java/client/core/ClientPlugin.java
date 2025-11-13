/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.Collections;
import java.util.List;

/**
 * A plugin modifies a client's configuration when the client is created or at request execution time.
 *
 * <h2>Plugin Application</h2>
 * <p>Plugins are applied when a {@link ClientConfig} is built via {@link ClientConfig.Builder#build()}.
 * Each plugin class is applied only once per configuration, even if added multiple times. Plugins are
 * sorted by {@link Phase} and then by insertion order before application.
 *
 * <h2>Deduplication</h2>
 * <p>Plugin deduplication is based on the plugin's class, not its instance. This means:
 * <ul>
 *   <li>Multiple instances of {@code MyPlugin.class} will only apply once</li>
 *   <li>Singleton patterns work seamlessly: {@code DefaultPlugin.INSTANCE}</li>
 *   <li>Anonymous classes create unique classes each time, so each applies separately</li>
 *   <li>Lambda-based plugins also create unique classes and apply separately</li>
 * </ul>
 *
 * <h2>Child Plugins</h2>
 * <p>Plugins can return child plugins via {@link #getChildPlugins()}. Child plugins:
 * <ul>
 *   <li>Are only applied if the parent plugin passes the {@code pluginPredicate}</li>
 *   <li>Are independently tested against the {@code pluginPredicate}</li>
 *   <li>Can run in different phases than their parent</li>
 *   <li>Are deduplicated just like top-level plugins</li>
 * </ul>
 *
 * <h2>Configuration Reuse</h2>
 * <p>When using {@link ClientConfig#toBuilder()}, the returned builder remembers what plugin classes
 * were already applied. Calling {@code build()} on this builder will not re-apply those plugins,
 * preventing duplicate configuration. This enables a kind of config template pattern:
 *
 * <pre>{@code
 * var baseConfig = ClientConfig.builder()
 *     .addPlugin(new MyPlugin())
 *     .build();
 *
 * var derivedConfig = baseConfig.toBuilder()
 *     .addPlugin(new AnotherPlugin())  // Only AnotherPlugin applies
 *     .build();
 * }</pre>
 *
 * <h2>Request Overrides</h2>
 * <p>When applying plugins via {@link ClientConfig#withRequestOverride(RequestOverrideConfig)},
 * plugins that were applied during client construction will not be re-applied. Only new plugins
 * in the override will be applied. Override plugins are sorted among themselves by phase but cannot
 * be reordered relative to constructor plugins since those have already been applied.
 *
 * <h2>Plugin Predicates</h2>
 * <p>A {@code pluginPredicate} can filter what plugins are applied via
 * {@link ClientConfig.Builder#pluginPredicate()}. Only plugins that pass the predicate are applied and tracked as
 * applied. If a plugin was rejected in one config, it can be applied in a derived config if the predicate changes to
 * accept it.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * public class MyPlugin implements ClientPlugin {
 *     @Override
 *     public void configureClient(ClientConfig.Builder config) {
 *         config.addInterceptor(new MyInterceptor());
 *     }
 * }
 *
 * // Using with a client
 * var client = DynamicClient.builder()
 *     .addPlugin(new MyPlugin())
 *     .addPlugin(new MyPlugin())  // Skipped because it's already applied
 *     .build();
 * }</pre>
 *
 * @see ClientConfig.Builder#addPlugin(ClientPlugin)
 * @see Client.Builder#addPlugin(ClientPlugin)
 * @see AutoClientPlugin
 */
@FunctionalInterface
public interface ClientPlugin {
    /**
     * Modify the provided client configuration.
     *
     * <p>Note: plugins should be added and applied to a client using {@link Client.Builder#addPlugin(ClientPlugin)} or
     * to a {@code ClientConfig} using {@link ClientConfig.Builder#addPlugin(ClientPlugin)}.
     */
    void configureClient(ClientConfig.Builder config);

    /**
     * Gets the child plugins that this plugin applies, allowing the child plugins to be applied to a client in the
     * correct phase.
     *
     * <p>Using child plugins is preferred over applying plugins inside another plugin. Using child plugins
     * allows a plugin to aggregate other plugins, allows child plugins to run in different phases, and goes through
     * the plugin predicate of a client builder.
     *
     * @return the child plugins this plugin applies.
     */
    default List<ClientPlugin> getChildPlugins() {
        return Collections.emptyList();
    }

    /**
     * Get the phase in which the plugin should fire.
     *
     * @return the plugin phase.
     */
    default Phase getPluginPhase() {
        return Phase.APPLY;
    }

    /**
     * Gets the phase in which the plugin is applied to the client.
     */
    enum Phase {
        /**
         * The first phase, typically reserved for framework-level plugins. For example, the default client plugin is
         * applied first, during this phase.
         */
        FIRST,

        /**
         * A phase fired after FIRST and before attempting to add defaults to the client.
         *
         * <p>This phase can be used to apply validation after FIRST phases have completed. Or as a way to apply
         * defaults before other plugins attempt to apply defaults.
         */
        BEFORE_DEFAULTS,

        /**
         * A phase used to apply defaults to the client.
         */
        DEFAULTS,

        /**
         * A phase fired after defaults have been applied to the client.
         */
        AFTER_DEFAULTS,

        /**
         * A phase fired before more general plugin mutations are applied to the client.
         */
        BEFORE_APPLY,

        /**
         * A phase used to apply more general plugin mutations to the client. This is the default phase is none is
         * specified.
         */
        APPLY,

        /**
         * A phase fired after more general plugin mutations have been applied to the client.
         */
        AFTER_APPLY,

        /**
         * The last plugin phase, typically reserved for framework-level plugins.
         */
        LAST
    }
}
