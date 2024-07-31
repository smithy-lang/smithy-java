/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import java.net.URI;
import java.util.List;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.ServerBuilder;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.ServiceMatcher;

public class NettyServerBuilder extends ServerBuilder<NettyServerBuilder> {

    List<Service> services;
    ServiceMatcher serviceMatcher;
    List<URI> endpoints;

    @Override
    public NettyServerBuilder endpoints(URI... endpoints) {
        this.endpoints = List.of(endpoints);
        return self();
    }

    @Override
    protected NettyServerBuilder addServices(List<Service> services) {
        this.services = services;
        return self();
    }

    @Override
    protected NettyServerBuilder setServiceMatcher(ServiceMatcher serviceMatcher) {
        this.serviceMatcher = serviceMatcher;
        return null;
    }

    @Override
    protected Server buildServer() {
        validate();
        return new NettyServer(this);
    }

    private void validate() {
        //TODO add validations
    }
}
