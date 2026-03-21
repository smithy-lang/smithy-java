/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.codegen.core.Property;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Contains server-specific properties that may be added to symbols by smithy-java.
 *
 * @see software.amazon.smithy.java.codegen.SymbolProperties for other properties that may be added to symbols.
 */
@SmithyUnstableApi
public final class ServerSymbolProperties {

    private ServerSymbolProperties() {}

    /**
     * Symbol representing the async variant of the operation stub.
     */
    public static final Property<Symbol> ASYNC_STUB_OPERATION = Property.named("async-stub-operation");

    /**
     * Symbol representing the sync variant of the operation stub.
     */
    public static final Property<Symbol> STUB_OPERATION = Property.named("stub-operation");

    /**
     * Symbol representing the generated operation model class.
     */
    public static final Property<Symbol> API_OPERATION = Property.named("api-operation");

    /**
     * Name to use for the operation when used as a field inside the service.
     */
    public static final Property<String> OPERATION_FIELD_NAME = Property.named("operation-field-name");

    /**
     * Namespace under which types of this service would be generated.
     */
    public static final Property<String> TYPES_NAMESPACE = Property.named("types-namespace");

    /**
     * Symbol for the server service class, used in combined client+server mode.
     *
     * <p>When both CLIENT and SERVER modes are active, the symbol provider returns a client service symbol.
     * This property holds the server service symbol so that the ServiceGenerator can retrieve it.
     */
    public static final Property<Symbol> SERVER_SERVICE_SYMBOL = Property.named("server-service-symbol");
}
