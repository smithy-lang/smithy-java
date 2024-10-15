/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import software.amazon.smithy.java.runtime.core.schema.Schema;

/**
 * Entry point for handling HTTP bindings.
 */
public final class HttpBinding {

    private final ConcurrentMap<Schema, BindingMatcher> REQUEST_CACHE = new ConcurrentHashMap<>();
    private final ConcurrentMap<Schema, BindingMatcher> RESPONSE_CACHE = new ConcurrentHashMap<>();

    public HttpBinding() {}

    /**
     * Create an HTTP binding request serializer.
     *
     * @return Returns the serializer.
     */
    public RequestSerializer requestSerializer() {
        return new RequestSerializer(REQUEST_CACHE);
    }

    /**
     * Create an HTTP binding response serializer.
     *
     * @return Returns the serializer.
     */
    public ResponseSerializer responseSerializer() {
        return new ResponseSerializer(RESPONSE_CACHE);
    }

    /**
     * Create an HTTP binding request deserializer.
     *
     * @return Returns the request deserializer.
     */
    public RequestDeserializer requestDeserializer() {
        return new RequestDeserializer(REQUEST_CACHE);
    }

    /**
     * Create an HTTP binding response deserializer.
     *
     * @return Returns the response deserializer.
     */
    public ResponseDeserializer responseDeserializer() {
        return new ResponseDeserializer(RESPONSE_CACHE);
    }
}
