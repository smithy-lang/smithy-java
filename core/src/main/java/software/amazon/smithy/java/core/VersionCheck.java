/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

import java.util.ArrayList;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.versionspi.ModuleVersion;
import software.amazon.smithy.java.versionspi.SmithyVersionProvider;

/**
 * Validates that all Smithy Java modules on the classpath have compatible versions.
 *
 * <p>Mixing different versions of Smithy Java modules in the same application can cause
 * subtle runtime errors such as missing methods, class not found exceptions, or unexpected
 * behavior. This commonly happens when different dependencies pull in different versions of
 * the same module transitively. This check detects such mismatches early, during client or
 * server initialization, before any operation is executed.
 *
 * <p>This check uses {@link ServiceLoader} to discover all {@link SmithyVersionProvider}
 * implementations on the classpath. Each Smithy Java module registers a provider via
 * {@code META-INF/services}, which is correctly merged by fat JAR tools.
 *
 * <p>The check can be disabled by setting the system property
 * {@code smithy.java.skipVersionCheck} to {@code true}.
 */
public final class VersionCheck {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(VersionCheck.class);
    private static final String SKIP_VERSION_CHECK_PROPERTY = "smithy.java.skipVersionCheck";
    private static final AtomicBoolean CHECKED = new AtomicBoolean(false);

    private VersionCheck() {}

    /**
     * Validates version compatibility of all Smithy Java modules on the classpath.
     *
     * <p>This method is safe to call multiple times; the check is performed only once.
     *
     * @param codegenVersion the version the code was generated against, as a {@link ModuleVersion}
     * @throws IncompatibleVersionException if a version mismatch is detected
     */
    public static void check(ModuleVersion codegenVersion) {
        if (CHECKED.get()) {
            return;
        }
        if (Boolean.getBoolean(SKIP_VERSION_CHECK_PROPERTY)) {
            LOGGER.warn("Smithy Java version compatibility check is disabled via '{}'. "
                    + "This is not recommended and should only be used as a temporary workaround. "
                    + "Running with mismatched module versions may cause unexpected runtime errors.",
                    SKIP_VERSION_CHECK_PROPERTY);
            CHECKED.set(true);
            return;
        }

        var modules = new ArrayList<ModuleVersion>();
        for (var provider : ServiceLoader.load(SmithyVersionProvider.class)) {
            modules.add(provider.getModuleVersion());
        }

        if (modules.isEmpty()) {
            CHECKED.set(true);
            return;
        }

        var errors = new ArrayList<String>();

        // All modules must report the same version.
        var firstVersion = modules.get(0);
        for (var module : modules) {
            if (module.compareTo(firstVersion) != 0) {
                errors.add("Version mismatch: module '" + firstVersion.moduleName() + "' has version "
                        + firstVersion.versionString() + " but module '" + module.moduleName()
                        + "' has version " + module.versionString());
            }
        }

        // All module versions must be >= the codegen version.
        for (var module : modules) {
            if (module.compareTo(codegenVersion) < 0) {
                errors.add("Module '" + module.moduleName() + "' version " + module.versionString()
                        + " is older than the codegen version " + codegenVersion.versionString());
            }
        }

        if (!errors.isEmpty()) {
            // Build a nice error message to give the end-user all the details needed
            // to fix the issue.
            var sb = new StringBuilder("Smithy Java version compatibility check failed:\n");
            sb.append("  Generated with version: ").append(codegenVersion.versionString()).append("\n");
            sb.append("  Modules on classpath:\n");
            for (var module : modules) {
                sb.append("    - ")
                        .append(module.moduleName())
                        .append(" = ")
                        .append(module.versionString())
                        .append("\n");
            }
            sb.append("  Issues:\n");
            for (var error : errors) {
                sb.append("    - ").append(error).append("\n");
            }
            sb.append("  Fix: Align all smithy-java dependencies to the same version. ")
                    .append("If using Gradle, consider importing the BOM: ")
                    .append("platform('software.amazon.smithy.java:bom:")
                    .append(codegenVersion.versionString())
                    .append("')");
            throw new IncompatibleVersionException(sb.toString());
        }

        CHECKED.set(true);
    }

    /**
     * Resets the check state. Visible for testing and benchmarking only.
     */
    static void reset() {
        CHECKED.set(false);
    }

}
