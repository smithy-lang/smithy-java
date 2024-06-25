/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server;

import software.amazon.smithy.codegen.core.Property;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * TODO: DOCS
 */
public final class ServerSymbolProperties {

    private ServerSymbolProperties() {

    }

    /**
     * TODO: DOCS
     */
    public static final Property<Symbol> ASYNC_STUB_OPERATION = Property.named("async-stub-operation");

    /**
     * TODO: DOCS
     */
    public static final Property<Symbol> STUB_OPERATION = Property.named("stub-operation");

    /**
     * TODO: DOCS
     */
    public static final Property<String> OPERATION_FIELD_NAME = Property.named("operation-field-name");

    /**
     *  Symbol for the generated @{@link software.amazon.smithy.java.runtime.core.schema.ApiOperation}
     */
    public static final Property<Symbol> API_OPERATION_SYMBOL = Property.named("api-operation");
}
