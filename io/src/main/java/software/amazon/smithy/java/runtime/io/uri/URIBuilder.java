/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.uri;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A builder for a {@link URI}.
 *
 * <p>All methods of this builder expect that the provided values are valid for their components and are already
 * percent-encoded where necessary.
 */
public final class URIBuilder {

    private String scheme;
    private String userInfo;
    private String host;
    private int port = -1;
    private String path; // this shouldn't default to "/"
    private String query;
    private String fragment;

    /**
     * Create a builder from a URI.
     *
     * @param uri URI to decompose into a builder.
     * @return Returns the builder.
     */
    public static URIBuilder of(URI uri) {
        URIBuilder builder = new URIBuilder();
        builder.scheme(uri.getScheme());
        builder.userInfo(uri.getRawUserInfo());
        builder.host(uri.getHost());
        builder.port(uri.getPort());
        builder.path(uri.getRawPath());
        builder.query(uri.getRawQuery());
        builder.fragment(uri.getRawFragment());
        return builder;
    }

    /**
     * Builds the URI from the components.
     *
     * @return Returns the built URI.
     * @throws IllegalArgumentException if the URI cannot be built.
     * @throws NullPointerException if parts of the URI are missing.
     */
    public URI build() {
        try {
            return new URI(buildString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String buildString() {
        final StringBuilder builder = new StringBuilder();

        if (this.scheme != null) {
            builder.append(this.scheme).append(':');
        }

        if (this.host != null) {
            builder.append("//");
            if (userInfo != null) {
                builder.append(userInfo).append('@');
            }
            builder.append(host);
            if (port >= 0) {
                builder.append(':').append(port);
            }
        }

        if (path != null) {
            if (host != null && !path.isEmpty() && !path.startsWith("/")) {
                builder.append('/');
            }
            builder.append(this.path);
        }

        if (query != null) {
            builder.append('?').append(query);
        }

        if (fragment != null) {
            builder.append('#').append(fragment);
        }

        return builder.toString();
    }

    public URIBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    public URIBuilder userInfo(String userInfo) {
        this.userInfo = userInfo;
        return this;
    }

    public URIBuilder host(String host) {
        this.host = host;
        return this;
    }

    public URIBuilder port(int port) {
        this.port = port;
        return this;
    }

    public URIBuilder path(String path) {
        this.path = path;
        return this;
    }

    public URIBuilder query(String query) {
        this.query = query;
        return this;
    }

    public URIBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    /**
     * Concatenate the given raw path string onto the URI, adding a "/" if necessary.
     *
     * @param path Path to concatenate onto the URI.
     * @return Returns the builder.
     */
    public URIBuilder concatPath(String path) {
        if (this.path.endsWith("/")) {
            if (path.startsWith("/")) {
                this.path += path.substring(1);
            } else {
                this.path += path;
            }
        } else if (path.startsWith("/")) {
            this.path += path;
        } else {
            this.path += "/" + path;
        }
        return this;
    }
}
