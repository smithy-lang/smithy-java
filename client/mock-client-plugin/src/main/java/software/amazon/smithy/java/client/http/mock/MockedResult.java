/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.server.core.ServerProtocol;

/**
 * A mocked result to return from a {@link MockPlugin}.
 */
public sealed interface MockedResult {
    /**
     * Return a serialized output shape using the given protocol.
     *
     * @param output   Shape to serialize.
     * @param protocol Custom and optional protocol used to serialize the shape.
     */
    record Output(SerializableStruct output, ServerProtocol protocol) implements MockedResult {
        /**
         * Serializes the output by dynamically detecting the protocol used when a call is made.
         *
         * <p>If a corresponding server protocol cannot be found for the dynamically detected client protocol, then
         * a {@link IllegalArgumentException} is thrown at that time.
         *
         * @param output Output to serialize.
         */
        public Output(SerializableStruct output) {
            this(output, null);
        }
    }

    /**
     * Returns a specific HTTP response.
     *
     * @param response Response to return.
     */
    record Response(HttpResponse response) implements MockedResult {}

    /**
     * Throws a specific exception directly rather than serializing it.
     *
     * <p>To serialize and then throw an exception, create the modeled exception and create an {@link Output} result.
     *
     * @param e The error to throw.
     */
    record Error(Throwable e) implements MockedResult {}
}
