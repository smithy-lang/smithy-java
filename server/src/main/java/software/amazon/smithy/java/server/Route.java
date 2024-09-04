/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.List;

public final class Route {

    private final String hostName;
    private final String pathPrefix;
    private final Integer port;
    private final String protocol;
    private final List<Service> services;

    private Route(Builder builder) {
        this.hostName = builder.hostName;
        this.pathPrefix = builder.pathPrefix;
        this.port = builder.port;
        this.protocol = builder.protocol;
        this.services = builder.services;
    }

    public String getHostName() {
        return hostName;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public Integer getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public List<Service> getServices() {
        return services;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String hostName;
        private String pathPrefix;
        private Integer port;
        private String protocol;
        private List<Service> services;

        public Builder hostName(String hostName) {
            this.hostName = hostName;
            return this;
        }

        public Builder pathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder services(List<Service> services) {
            this.services = services;
            return this;
        }

        public Route build() {
            return new Route(this);
        }

    }
}
