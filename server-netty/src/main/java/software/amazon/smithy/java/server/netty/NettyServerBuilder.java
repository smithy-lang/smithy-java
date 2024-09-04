/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import java.net.URI;
import java.util.List;
import software.amazon.smithy.java.server.Route;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.ServerBuilder;
import software.amazon.smithy.java.server.core.ServiceMatcher;

final class NettyServerBuilder extends ServerBuilder<NettyServerBuilder> {

    ServiceMatcher serviceMatcher;
    List<URI> endpoints;
    int numberOfWorkers = Runtime.getRuntime().availableProcessors() * 2;

    @Override
    public NettyServerBuilder endpoints(URI... endpoints) {
        this.endpoints = List.of(endpoints);
        return self();
    }

    @Override
    public NettyServerBuilder numberOfWorkers(int numberOfWorkers) {
        this.numberOfWorkers = numberOfWorkers;
        return self();
    }

    @Override
    protected NettyServerBuilder setServerRoutes(List<Route> routes) {
        this.serviceMatcher = new ServiceMatcher(routes);
        return null;
    }

    @Override
    protected Server buildServer() {
        validate();
        return new NettyServer(this);
    }

    private void validate() {
        if (numberOfWorkers <= 0) {
            throw new IllegalArgumentException("Number of workers must be greater than zero");
        }
    }
}
