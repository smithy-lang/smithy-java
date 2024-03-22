/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

/**
 * Entry point for handling HTTP bindings.
 */
public final class HttpBinding {

    private HttpBinding() {
    }

    /**
     * Create an HTTP binding request serializer.
     *
     * @return Returns the serializer.
     */
    public static RequestSerializer requestSerializer() {
        return new RequestSerializer();
    }

    /**
     * Create an HTTP binding response serializer.
     *
     * @return Returns the serializer.
     */
    public static ResponseSerializer responseSerializer() {
        return new ResponseSerializer();
    }

    /**
     * Create an HTTP binding request deserializer.
     *
     * @return Returns the request deserializer.
     */
    public static Object requestDeserializer() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Create an HTTP binding response deserializer.
     *
     * @return Returns the response deserializer.
     */
    public static ResponseDeserializer responseDeserializer() {
        return new ResponseDeserializer();
    }
}
