/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class ServerBuilder<T extends ServerBuilder<T>> {

    private static final URI DEFAULT_ENDPOINT = URI.create("http://localhost:8080");
    private final Map<String, List<Service>> servicePathMappings = new HashMap<>();
    private final List<Route> routes = new ArrayList<>();

    public final Server build() {
        if (routes.isEmpty()) {
            for (var entry : servicePathMappings.entrySet()) {
                var serviceMatcher = Route.builder()
                    .pathPrefix(entry.getKey())
                    .services(entry.getValue())
                    .build();
                routes.add(serviceMatcher);
            }
        }
        setServerRoutes(routes);

        return buildServer();
    }

    public final T endpoints(int... ports) {
        if (ports == null || ports.length == 0) {
            return endpoints(DEFAULT_ENDPOINT);
        }
        URI[] endpoints = new URI[ports.length];
        for (int i = 0; i < ports.length; i++) {
            endpoints[i] = URI.create("http://localhost:" + ports[i]);
        }
        return endpoints(endpoints);
    }

    public final T addService(String path, Service service) {
        if (!routes.isEmpty()) {
            throw new IllegalStateException(
                "Either use addService(ServiceDefinition...) or addService(Service...), not both"
            );
        }
        servicePathMappings.computeIfAbsent(path, k -> new ArrayList<>()).add(service);
        return self();
    }

    public final T addService(Service service) {
        return addService("/", service);
    }

    public final T addService(Route route) {
        if (!servicePathMappings.isEmpty()) {
            throw new IllegalStateException(
                "Either use addService(ServiceDefinition...) or addService(Service...), not both"
            );
        }
        routes.add(Objects.requireNonNull(route, "A non-null service matcher is required"));
        return self();
    }

    public abstract T endpoints(URI... endpoints);

    public abstract T numberOfWorkers(int numberOfWorkers);

    protected abstract T setServerRoutes(List<Route> routes);

    protected abstract Server buildServer();

    protected final T self() {
        return (T) this;
    }

}
