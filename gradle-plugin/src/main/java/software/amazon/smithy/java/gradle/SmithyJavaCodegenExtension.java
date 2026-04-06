/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle;

import java.util.Collections;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * DSL extension for the smithy-java codegen Gradle plugin.
 *
 * <p>Configure via the {@code smithyJava} block:
 * <pre>{@code
 * smithyJava {
 *     autoAddDependencies = true       // default
 *     additionalSmithyBuildPlugins = ["trait-codegen"]
 *     mergeServiceFiles = true         // default
 * }
 * }</pre>
 */
public abstract class SmithyJavaCodegenExtension {

    public SmithyJavaCodegenExtension() {
        getAutoAddDependencies().convention(true);
        getAdditionalSmithyBuildPlugins().convention(Collections.emptyList());
        getMergeServiceFiles().convention(true);
    }

    /**
     * Whether to automatically add dependencies based on the modes configured in
     * {@code smithy-build.json}. When enabled (default), the plugin parses the
     * smithy-build configuration to determine which codegen modes are active and
     * adds the appropriate dependencies:
     *
     * <ul>
     *     <li>Always: {@code codegen-plugin} to smithyBuild; {@code core} and
     *     {@code framework-errors} to api</li>
     *     <li>Client mode: {@code client-core} to smithyBuild and api</li>
     *     <li>Server mode: {@code server-api} to smithyBuild and api</li>
     * </ul>
     *
     * <p>Set to {@code false} to manage all dependencies manually.
     *
     * @return property controlling auto-dependency management
     */
    public abstract Property<Boolean> getAutoAddDependencies();

    /**
     * Additional Smithy build plugin names (as declared in {@code smithy-build.json})
     * whose generated output should be wired into the Java source set. The
     * {@code java-codegen} plugin output is always wired automatically.
     *
     * <p>For example, add {@code "trait-codegen"} if your {@code smithy-build.json}
     * uses the trait-codegen plugin alongside java-codegen.
     *
     * @return list of additional Smithy build plugin names
     */
    public abstract ListProperty<String> getAdditionalSmithyBuildPlugins();

    /**
     * Whether to automatically merge {@code META-INF/services} files when
     * {@link #getAdditionalSmithyBuildPlugins()} is non-empty. Defaults to {@code true}.
     *
     * <p>When multiple Smithy build plugins produce service provider files, they
     * may conflict. This option enables a merge task that combines them.
     *
     * @return property controlling service file merging
     */
    public abstract Property<Boolean> getMergeServiceFiles();
}
