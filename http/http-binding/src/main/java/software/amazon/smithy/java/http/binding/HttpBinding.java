/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.core.schema.Schema;

/**
 * Entry point for handling HTTP bindings.
 */
public final class HttpBinding {

    public HttpBinding() {}

    /**
     * Create an HTTP binding request serializer.
     *
     * @return Returns the serializer.
     */
    public RequestSerializer requestSerializer() {
        return new RequestSerializer();
    }

    /**
     * Create an HTTP binding response serializer.
     *
     * @return Returns the serializer.
     */
    public ResponseSerializer responseSerializer() {
        return new ResponseSerializer();
    }

    /**
     * Create an HTTP binding request deserializer.
     *
     * @return Returns the request deserializer.
     */
    public RequestDeserializer requestDeserializer() {
        return new RequestDeserializer();
    }

    /**
     * Create an HTTP binding response deserializer.
     *
     * @return Returns the response deserializer.
     */
    public ResponseDeserializer responseDeserializer() {
        return new ResponseDeserializer();
    }

    /**
     * Whether the given input-struct schema's request binding has an {@code @httpPayload}
     * member whose target is a {@code STRUCTURE} — i.e., the request body is a single
     * codec-serialized struct rather than headers + member-derived body fields.
     *
     * @param inputSchema the operation input struct schema.
     * @return {@code true} iff the schema has a struct-typed @httpPayload member.
     */
    public boolean hasStructPayload(Schema inputSchema) {
        return HttpBindingSchemaExtensions.structBindingsOf(inputSchema).request().hasStructPayload;
    }
}
