/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.mcp.bundle.api.Registry;

class RegistryUtils {

    private RegistryUtils() {}

    private static final Map<String, Registry> JAVA_REGISTRIES;

    static {
        JAVA_REGISTRIES = ServiceLoader.load(Registry.class)
                .stream()
                .map(Provider::get)
                .collect(Collectors.toMap(Registry::name, Function.identity()));
    }

    static Registry getRegistry(String name, Config config) {

        if (name == null) {
            name = Objects.requireNonNull(config.getDefaultRegistry(),
                    "Either configure a default registry or the registry name is required.");
        }

        if (!config.getRegistries().containsKey(name)) {
            throw new IllegalArgumentException("The registry '" + name + "' does not exist.");
        }

        if (JAVA_REGISTRIES.containsKey(name)) {
            return JAVA_REGISTRIES.get(name);
        }
        throw new IllegalStateException("No such registry: " + name);
    }
}
