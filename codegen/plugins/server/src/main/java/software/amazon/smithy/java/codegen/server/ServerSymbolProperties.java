/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server;

import software.amazon.smithy.codegen.core.Property;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Contains server-specific properties that may be added to symbols by smithy-java.
 *
 * @see software.amazon.smithy.java.codegen.SymbolProperties for other properties that may be added to symbols.
 */
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
}
