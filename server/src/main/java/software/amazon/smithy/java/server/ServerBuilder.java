/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class ServerBuilder<T extends ServerBuilder<T>> {

    private static final URI DEFAULT_ENDPOINT = URI.create("http://localhost:8080");
    private Map<String, List<Service>> servicePathMappings = new HashMap<>();
    private boolean customServiceMatcherSet = false;

    public final Server build() {
        addServices(servicePathMappings.values().stream().flatMap(Collection::stream).toList());
        //If all services are just added to "/", we don't set a ServiceMatcher. Or if the user has set their own service matcher
        if (!customServiceMatcherSet && !(servicePathMappings.size() == 1 && servicePathMappings.containsKey("/"))) {
            setServiceMatcher(new ExactPathServiceMatcher(Collections.unmodifiableMap(servicePathMappings)));
        }

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
        servicePathMappings.computeIfAbsent(path, k -> new ArrayList<>()).add(service);
        return self();
    }

    public final T addService(Service service) {
        return addService("/", service);
    }

    public abstract T endpoints(URI... endpoints);

    protected abstract T addServices(List<Service> services);

    public final T serviceMatcher(ServiceMatcher serviceMatcher) {
        customServiceMatcherSet = true;
        return setServiceMatcher(Objects.requireNonNull(serviceMatcher, "A non-null service matcher is required"));
    }

    protected abstract T setServiceMatcher(ServiceMatcher serviceMatcher);

    protected abstract Server buildServer();

    protected final T self() {
        return (T) this;
    }

    private record ExactPathServiceMatcher(Map<String, List<Service>> servicePathMappings) implements ServiceMatcher {

        @Override
        public List<Service> getCandidateServices(ServiceMatcherInput input) {
            return servicePathMappings.get(input.getRequestPath());
        }
    }

}
