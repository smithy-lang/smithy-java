/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import software.amazon.smithy.java.runtime.api.EndpointProvider;
import software.amazon.smithy.java.runtime.context.Context;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

/**
 * Context parameters made available to underlying transports like HTTP clients.
 */
public final class CallContext {
    /**
     * Contains the input of the operation being sent.
     */
    public static final Context.Key<SerializableShape> INPUT = Context.key("Input shape");

    /**
     * Deserialized output of the call.
     */
    public static final Context.Key<SerializableShape> OUTPUT = Context.key("Output");

    /**
     * Error encountered by the call that will be thrown.
     */
    public static final Context.Key<SdkException> ERROR = Context.key("Error");

    /**
     * Contains the schema of the operation being sent.
     */
    public static final Context.Key<SdkSchema> OPERATION_SCHEMA = Context.key("Operation schema");

    /**
     * Contains the input schema of the operation being sent.
     */
    public static final Context.Key<SdkSchema> INPUT_SCHEMA = Context.key("Input schema");

    /**
     * Contains the output schema of the operation being sent.
     */
    public static final Context.Key<SdkSchema> OUTPUT_SCHEMA = Context.key("Output schema");

    /**
     * The endpoint provider used to resolve the destination endpoint for a request.
     */
    public static final Context.Key<EndpointProvider> ENDPOINT_PROVIDER = Context.key("EndpointProvider");

    private CallContext() {}
}
