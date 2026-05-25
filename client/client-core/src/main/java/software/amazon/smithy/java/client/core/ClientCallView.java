/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.Map;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A read-only view of a resolved client call, exposed to {@link CallDecorator} implementations.
 *
 * <p>The view reflects configuration <em>after</em> any per-request overrides and {@code modifyBeforeCall}
 * interceptor adjustments have been merged, so decorators see the same effective configuration that the
 * pipeline will use.
 *
 * @param <I> the operation input type.
 * @param <O> the operation output type.
 */
public sealed interface ClientCallView<I extends SerializableStruct, O extends SerializableStruct> permits ClientCall {
    /**
     * @return the operation input.
     */
    I input();

    /**
     * @return the API operation being invoked.
     */
    ApiOperation<I, O> operation();

    /**
     * Returns the call's mutable {@link Context}. Mutations made by a decorator are visible to
     * interceptors and the rest of the call.
     *
     * @return the call context.
     */
    Context context();

    /**
     * @return the resolved endpoint resolver for this call.
     */
    EndpointResolver endpointResolver();

    /**
     * @return the type registry for this call.
     */
    TypeRegistry typeRegistry();

    /**
     * @return the resolved auth scheme resolver for this call.
     */
    AuthSchemeResolver authSchemeResolver();

    /**
     * @return the auth schemes supported by this call, keyed by scheme ID.
     */
    Map<ShapeId, AuthScheme<?, ?>> supportedAuthSchemes();

    /**
     * @return the identity resolvers available for this call.
     */
    IdentityResolvers identityResolvers();

    /**
     * Creates a new ClientCallView with a different, compatible Input.
     *
     * @param newInput the input to substitute (must not be {@code null}).
     * @return a view backed by the same call state with the new input.
     */
    ClientCallView<I, O> withInput(I newInput);
}
