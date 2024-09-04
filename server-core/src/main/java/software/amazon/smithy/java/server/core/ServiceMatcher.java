/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.server.Route;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public class ServiceMatcher {

    private final List<Service> allServices;
    private final List<Route> routes;
    private final List<Service> defaultServices;

    public ServiceMatcher(List<Route> routes) {
        this.routes = routes;
        if (routes.size() == 1) {
            var def = routes.get(0);
            if (def.getHostName() == null && def.getPort() == null && def.getProtocol() == null && "/".equals(
                def.getPathPrefix()
            )) {
                defaultServices = def.getServices();
            } else {
                defaultServices = null;
            }
        } else {
            defaultServices = null;
        }
        this.allServices = routes.stream().map(Route::getServices).flatMap(Collection::stream).toList();
    }

    public List<Service> getAllServices() {
        return allServices;
    }

    public List<Service> getCandidateServices(ServiceProtocolResolutionRequest request) {
        if (defaultServices != null) {
            return defaultServices;
        }
        URI uri = request.uri();
        String path = uri.getPath();
        int port = uri.getPort();
        String scheme = uri.getScheme();
        for (var definition : routes) {
            if (definition.getPathPrefix() != null && !path.startsWith(definition.getPathPrefix())) {
                continue;
            }
            if (!Objects.equals(definition.getPort(), port)) {
                continue;
            }
            if (!Objects.equals(definition.getProtocol(), scheme)) {
                continue;
            }
            return definition.getServices();
        }
        return List.of();
    }
}
