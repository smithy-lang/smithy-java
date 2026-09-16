/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.RuntimeCodegenMode;

/**
 * Entry point for handling HTTP bindings.
 */
public final class HttpBinding {

    private final RuntimeCodegenMode runtimeCodegen;

    public HttpBinding() {
        this(RuntimeCodegenFeature.resolve(null, HttpBindingCodegen.ID));
    }

    private HttpBinding(RuntimeCodegenMode runtimeCodegen) {
        this.runtimeCodegen = runtimeCodegen;
    }

    /** Returns a configurable HTTP binding builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an HTTP binding request serializer.
     *
     * @return Returns the serializer.
     */
    public RequestSerializer requestSerializer() {
        return new RequestSerializer(runtimeCodegen);
    }

    /**
     * Create an HTTP binding response serializer.
     *
     * @return Returns the serializer.
     */
    public ResponseSerializer responseSerializer() {
        return new ResponseSerializer(runtimeCodegen);
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

    /** Builds an {@link HttpBinding}. */
    public static final class Builder {

        private RuntimeCodegenMode runtimeCodegen;

        private Builder() {}

        /** Sets runtime codegen mode, or null to use system properties. */
        public Builder runtimeCodegen(RuntimeCodegenMode runtimeCodegen) {
            this.runtimeCodegen = runtimeCodegen;
            return this;
        }

        /** Returns whether runtime codegen resolves as enabled. */
        public boolean resolvedRuntimeCodegen() {
            return resolve() != RuntimeCodegenMode.DISABLED;
        }

        public HttpBinding build() {
            return new HttpBinding(resolve());
        }

        private RuntimeCodegenMode resolve() {
            return RuntimeCodegenFeature.resolve(runtimeCodegen, HttpBindingCodegen.ID);
        }
    }
}
