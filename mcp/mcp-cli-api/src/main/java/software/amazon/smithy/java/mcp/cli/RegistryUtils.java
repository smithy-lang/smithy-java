/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RegistryUtils {

    private RegistryUtils() {}

    private static final Map<String, Registry> JAVA_REGISTRIES;

    static {
        JAVA_REGISTRIES = ServiceLoader.load(Registry.class)
                .stream()
                .map(Provider::get)
                .collect(Collectors.toMap(Registry::name, Function.identity()));
    }

    public static Registry getRegistry(String name) {
        if (name == null) {
            return getRegistry();
        }
        if (JAVA_REGISTRIES.containsKey(name)) {
            return JAVA_REGISTRIES.get(name);
        }
        throw new IllegalStateException("No such registry: " + name);
    }

    public static Registry getRegistry() {
        return JAVA_REGISTRIES.values().iterator().next();
    }
}
