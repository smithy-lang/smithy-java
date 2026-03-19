/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ServiceLoader;
import software.amazon.smithy.java.client.core.plugins.AutoPlugin;
import software.amazon.smithy.java.client.core.plugins.DefaultPlugin;

/**
 * A marker interface and SPI used to automatically add plugins to clients when they're created.
 *
 * <p>Auto-plugins are discovered via Java's {@link ServiceLoader} mechanism and automatically
 * applied to all clients through {@link AutoPlugin}, a plugin added by default by the {@link DefaultPlugin}.
 *
 * <h2>When to Use Auto-Plugins</h2>
 * <p>Auto-plugins should be used sparingly and only when:
 * <ul>
 *   <li>The plugin provides cross-cutting functionality needed by all clients</li>
 *   <li>The plugin is conditional based on an opt-in signal (trait, context setting, etc.)</li>
 *   <li>The plugin adds protocol-specific functionality (e.g., HTTP-only features)</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <p>A well-designed auto-plugin is always predicated on some kind of opt-in signal:
 *
 * <pre>{@code
 * public class MyAutoPlugin implements AutoClientPlugin {
 *     @Override
 *     public void configureClient(ClientConfig.Builder config) {
 *         // Only apply if the opt-in signal is present
 *         if (config.context().get(MY_FEATURE_ENABLED) == Boolean.TRUE) {
 *             config.addInterceptor(new MyInterceptor());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Disabling Auto-Plugins</h2>
 * <p>Users can disable all auto-plugin discovery:
 * <pre>{@code
 * client.builder().disableAutoPlugins().build();
 * }</pre>
 *
 * @see AutoPlugin
 * @see Client.Builder#disableAutoPlugins()
 */
public interface AutoClientPlugin extends ClientPlugin {}
