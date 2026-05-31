/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

/**
 * Supplies the mutable containers a request is serialized into, so a transport can have the protocol
 * write headers (and, in future, the body) directly into the transport's own native representation
 * instead of a generic one that the transport must then copy.
 *
 * <p>This is a transport-agnostic seam: it vends only smithy-java {@link ModifiableHttpHeaders}, so
 * nothing about a specific transport (e.g. Netty buffers/headers) leaks into the protocol or
 * serialization layers. A transport advertises a factory; the protocol allocates its request headers
 * from it during {@code createRequest}. A transport that supplies no factory keeps the default
 * array-backed headers, so existing behavior is unchanged.
 *
 * <p>Implementations must be safe to share across requests (the protocol may call the factory once
 * per request); the returned headers instances are per-request and not shared.
 */
public interface HttpRequestFactory {
    /**
     * Allocate a mutable header set for a request being serialized.
     *
     * <p>The default returns the standard array-backed implementation. A transport overrides this to
     * return a {@link ModifiableHttpHeaders} backed by its own native header container, so the
     * protocol's header writes land directly in the transport's representation.
     *
     * @param expectedPairs hint for the expected number of header name/value pairs.
     * @return a fresh mutable header set.
     */
    default ModifiableHttpHeaders newRequestHeaders(int expectedPairs) {
        return HttpHeaders.ofModifiable(expectedPairs);
    }
}
