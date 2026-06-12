/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle;

import java.util.Collections;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * DSL extension for the smithy-java Gradle plugin.
 *
 * <p>Configure via the {@code smithyJava} block:
 * <pre>{@code
 * smithyJava {
 *     modes.add("client")                          // explicit mode declaration
 *     generatedPluginOutputs.add("trait-codegen")  // wire additional plugin output dirs
 *     mergeServiceFiles = true                     // default
 * }
 * }</pre>
 */
public abstract class SmithyJavaExtension {

    public SmithyJavaExtension() {
        getAutoAddDependencies().convention(true);
        getModes().convention(Collections.emptySet());
        getProjections().convention(Collections.emptyList());
        getGeneratedPluginOutputs().convention(Collections.emptyList());
        getMergeServiceFiles().convention(true);
    }

    /**
     * Whether to automatically add dependencies based on the codegen modes.
     * When enabled (default), the plugin adds the appropriate dependencies:
     *
     * <ul>
     *     <li>Always: {@code codegen-plugin} to smithyBuild; {@code core} and
     *     {@code framework-errors} to api/implementation</li>
     *     <li>Client mode: {@code client-core} to smithyBuild and api/implementation</li>
     *     <li>Server mode: {@code server-api} to smithyBuild and api/implementation</li>
     * </ul>
     *
     * <p>When {@code java-library} is applied, all runtime dependencies use {@code api}
     * unless the mode is server-only, in which case {@code implementation} is used.
     * When only {@code java} or {@code application} is applied, all dependencies use
     * {@code implementation}.
     *
     * <p>Set to {@code false} to manage all dependencies manually.
     *
     * @return property controlling auto-dependency management
     */
    public abstract Property<Boolean> getAutoAddDependencies();

    /**
     * Codegen modes to use for dependency resolution. When non-empty, these take
     * precedence over modes inferred from {@code smithy-build.json}. Valid values
     * are {@code "types"}, {@code "client"}, and {@code "server"}.
     *
     * <p>Example:
     * <pre>{@code
     * smithyJava {
     *     modes.add("client")
     *     modes.add("server")
     * }
     * }</pre>
     *
     * <p>When empty (default), modes are inferred by parsing the {@code java-codegen}
     * plugin configuration in {@code smithy-build.json}.
     *
     * @return set of explicitly declared codegen modes
     */
    public abstract SetProperty<String> getModes();

    /**
     * Explicit list of projection names whose {@code java-codegen} output should be
     * wired into the main source set. When non-empty, these are used instead of
     * the default source projection.
     *
     * <p>This is useful for multi-projection builds where each projection generates
     * code for a different service or protocol:
     * <pre>{@code
     * smithyJava {
     *     projections.addAll("rest-json-client", "rpc-v2-cbor-client")
     * }
     * }</pre>
     *
     * <p>When empty (default), the plugin uses the single source projection from
     * the {@code smithy} extension.
     *
     * @return list of projection names to wire
     */
    public abstract ListProperty<String> getProjections();

    /**
     * Additional Smithy build plugin names (as declared in {@code smithy-build.json})
     * whose generated output directories should be wired into the Java source set.
     * The {@code java-codegen} plugin output is always wired automatically.
     *
     * <p>For example, add {@code "trait-codegen"} if your {@code smithy-build.json}
     * uses the trait-codegen plugin alongside java-codegen and you want its output
     * compiled as part of this project.
     *
     * @return list of additional plugin output names to include
     */
    public abstract ListProperty<String> getGeneratedPluginOutputs();

    /**
     * Whether to automatically merge {@code META-INF/services} files when multiple
     * projections or {@link #getGeneratedPluginOutputs()} are configured. Defaults to
     * {@code true}.
     *
     * <p>When multiple projections or plugins produce service provider files, they
     * may conflict. This option enables a merge task that combines them.
     *
     * @return property controlling service file merging
     */
    public abstract Property<Boolean> getMergeServiceFiles();
}
