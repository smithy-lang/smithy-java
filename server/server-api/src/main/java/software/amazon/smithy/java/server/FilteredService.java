/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.framework.model.UnknownOperationException;

/**
 * A service implementation that filters operations based on a provided filter.
 * <p>
 * This class wraps an existing service and applies an operation filter to control
 * which operations are exposed through this service.
 */
public final class FilteredService implements Service {

    private final Service delegate;
    private final Predicate<ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>> filter;
    private final Map<String, Operation<? extends SerializableStruct, ? extends SerializableStruct>> filteredOperations;
    private final List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> operationList;

    /**
     * Creates a new filtered service by applying the specified filter to the delegate service.
     *
     * @param delegate The underlying service to filter
     * @param filter   The filter to apply to the service's operations
     */
    public FilteredService(
            Service delegate,
            Predicate<ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>> filter
    ) {
        this.delegate = delegate;
        this.filter = filter;
        this.filteredOperations = delegate.getAllOperations()
                .stream()
                .filter(o -> filter.test(o.getApiOperation()))
                .collect(Collectors.toMap(Operation::name, Function.identity()));
        this.operationList = new ArrayList<>(filteredOperations.values());
    }

    /**
     * Retrieves an operation by name, if it exists and passes the filter.
     *
     * @param operationName The name of the operation to retrieve
     * @return The filtered operation with the specified name
     * @throws UnknownOperationException if no matching operation is found, or it's filtered out
     */
    @Override
    public <I extends SerializableStruct,
            O extends SerializableStruct> Operation<I, O> getOperation(String operationName) {
        var operation = filteredOperations.get(operationName);
        if (operation == null) {
            throw UnknownOperationException.builder().message("No matching operations found for request").build();
        }
        return (Operation<I, O>) operation;
    }

    /**
     * Returns all operations that pass the filter.
     *
     * @return A list of all operations included by the filter
     */
    @Override
    public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
        return operationList;
    }

    /**
     * Returns the schema from the delegate service.
     *
     * @return The schema of the delegate service
     */
    @Override
    public Schema schema() {
        return delegate.schema(); //TODO this probably isn't accurate, we need to filter the schema too.
    }

    /**
     * Returns the type registry from the delegate service.
     *
     * @return The type registry of the delegate service
     */
    @Override
    public TypeRegistry typeRegistry() {
        return delegate.typeRegistry();
    }

    @Override
    public SchemaIndex schemaIndex() {
        return delegate.schemaIndex();
    }
}
