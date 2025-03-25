/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.iostream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import software.amazon.smithy.java.server.Route;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.ServerBuilder;
import software.amazon.smithy.java.server.core.ServiceMatcher;

public final class IOStreamServerBuilder extends ServerBuilder<IOStreamServerBuilder> {
    ServiceMatcher serviceMatcher;
    int numberOfWorkers = Runtime.getRuntime().availableProcessors() * 2;
    InputStream is;
    OutputStream os;

    @Override
    public IOStreamServerBuilder endpoints(URI... endpoints) {
        throw new UnsupportedOperationException("StdioServer only listens over stdio");
    }

    public IOStreamServerBuilder input(InputStream is) {
        this.is = is;
        return this;
    }

    public IOStreamServerBuilder output(OutputStream os) {
        this.os = os;
        return this;
    }

    public IOStreamServerBuilder stdio() {
        this.is = System.in;
        this.os = System.out;
        return this;
    }

    @Override
    public IOStreamServerBuilder numberOfWorkers(int numberOfWorkers) {
        this.numberOfWorkers = numberOfWorkers;
        return this;
    }

    @Override
    protected IOStreamServerBuilder setServerRoutes(List<Route> routes) {
        this.serviceMatcher = new ServiceMatcher(routes);
        return this;
    }

    @Override
    protected Server buildServer() {
        validate();
        return new StdioServer(this);
    }

    private void validate() {
        if (numberOfWorkers <= 0) {
            throw new IllegalArgumentException("Number of workers must be greater than zero");
        }
        if (is == null) {
            throw new IllegalArgumentException("No input stream provided");
        }
        if (os == null) {
            throw new IllegalArgumentException("No output stream provided");
        }
    }
}
