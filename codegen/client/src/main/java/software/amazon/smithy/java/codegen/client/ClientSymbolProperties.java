/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import software.amazon.smithy.codegen.core.Property;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Contains client-specific properties that may be added to symbols by smithy-java.
 *
 * @see software.amazon.smithy.java.codegen.SymbolProperties for other properties that may be added to symbols.
 */
public final class ClientSymbolProperties {

    /**
     * Indicates if a symbol represents an async implementation.
     */
    public static final Property<Boolean> ASYNC = Property.named("is-async");

    /**
     * Symbol representing the async implementation of Symbol.
     *
     * <p>This property is expected on all {@code Service} shape symbols.
     */
    public static final Property<Symbol> ASYNC_SYMBOL = Property.named("async-symbol");

    /**
     * Symbol representing the implementation class for a client.
     *
     * <p>This property is expected on all {@code Service} shape symbols.
     */
    public static final Property<Symbol> CLIENT_IMPL = Property.named("client-symbol");

    private ClientSymbolProperties() {}
}
