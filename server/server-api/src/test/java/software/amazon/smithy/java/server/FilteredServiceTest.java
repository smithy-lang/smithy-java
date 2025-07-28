/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static software.amazon.smithy.java.server.TestStructs.createMockOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.java.server.TestStructs.MockStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Tests for the {@link FilteredService} class.
 */
public class FilteredServiceTest {

    private MockService delegateService;

    @BeforeEach
    public void setup() {
        delegateService = new MockService();
    }

    /**
     * Test implementation of Service for testing FilteredService.
     */
    private class MockService implements Service {
        private final List<Operation<?, ?>> operations;
        private final Schema mockSchema = Schema.createService(ShapeId.from("mock#MockService"));
        private final TypeRegistry mockTypeRegistry = TypeRegistry.EMPTY;

        public MockService() {
            // Create a list of test operations
            operations = new ArrayList<>();
            operations.add(createMockOperation("GetItem"));
            operations.add(createMockOperation("PutItem"));
            operations.add(createMockOperation("UpdateItem"));
            operations.add(createMockOperation("DeleteItem"));
            operations.add(createMockOperation("ListItems"));
        }

        @Override
        public <I extends SerializableStruct,
                O extends SerializableStruct> Operation<I, O> getOperation(String operationName) {
            return (Operation<I, O>) operations.stream()
                    .filter(op -> op.name().equals(operationName))
                    .findFirst()
                    .orElseThrow(() -> UnknownOperationException.builder().build());
        }

        @Override
        public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
            return operations;
        }

        @Override
        public Schema schema() {
            return mockSchema;
        }

        @Override
        public TypeRegistry typeRegistry() {
            return mockTypeRegistry;
        }

        @Override
        public SchemaIndex schemaIndex() {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testFilteredServiceWithAllowList() {
        // Create a filtered service that only allows specific operations
        var filter = OperationFilters.allowList(Set.of("GetItem", "PutItem"));
        FilteredService filteredService = new FilteredService(delegateService, filter);

        // Verify that getAllOperations returns only the allowed operations
        List<Operation<?, ?>> filteredOperations = filteredService.getAllOperations();
        assertThat(filteredOperations).hasSize(2);
        assertThat(filteredOperations.stream().map(Operation::name).toList())
                .containsExactlyInAnyOrder("GetItem", "PutItem");

        // Verify that getOperation works for allowed operations
        Operation<?, ?> getItemOp = filteredService.getOperation("GetItem");
        assertThat(getItemOp).isNotNull();
        assertThat(getItemOp.name()).isEqualTo("GetItem");

        // Verify that getOperation throws for filtered-out operations
        assertThatThrownBy(() -> filteredService.getOperation("DeleteItem"))
                .isInstanceOf(UnknownOperationException.class);
    }

    @Test
    public void testFilteredServiceWithBlockList() {
        // Create a filtered service that blocks specific operations
        var filter = OperationFilters.blockList(Set.of("DeleteItem", "UpdateItem"));
        FilteredService filteredService = new FilteredService(delegateService, filter);

        // Verify that getAllOperations returns only the non-blocked operations
        List<Operation<?, ?>> filteredOperations = filteredService.getAllOperations();
        assertThat(filteredOperations).hasSize(3);
        assertThat(filteredOperations.stream().map(Operation::name).collect(Collectors.toList()))
                .containsExactlyInAnyOrder("GetItem", "PutItem", "ListItems");

        // Verify that getOperation works for non-blocked operations
        Operation<?, ?> listItemsOp = filteredService.getOperation("ListItems");
        assertThat(listItemsOp).isNotNull();
        assertThat(listItemsOp.name()).isEqualTo("ListItems");

        // Verify that getOperation throws for blocked operations
        assertThatThrownBy(() -> filteredService.getOperation("DeleteItem"))
                .isInstanceOf(UnknownOperationException.class);
    }

    @Test
    public void testFilteredServiceWithCustomFilter() {
        // Create a filtered service with a custom filter (only "Get" operations)
        Predicate<ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>> filter =
                operation -> operation.name().startsWith("Get");
        FilteredService filteredService = new FilteredService(delegateService, filter);

        // Verify that getAllOperations returns only the matching operations
        List<Operation<?, ?>> filteredOperations = filteredService.getAllOperations();
        assertThat(filteredOperations).hasSize(1);
        assertThat(filteredOperations.get(0).name()).isEqualTo("GetItem");

        // Verify that getOperation throws for filtered-out operations
        assertThatThrownBy(() -> filteredService.getOperation("PutItem"))
                .isInstanceOf(UnknownOperationException.class);
    }

    @Test
    public void testSchemaAndTypeRegistryDelegation() {
        // Create a filtered service
        var filter = OperationFilters.allowList(Set.of("GetItem"));
        FilteredService filteredService = new FilteredService(delegateService, filter);

        // Verify that schema and typeRegistry are delegated to the underlying service
        assertThat(filteredService.schema()).isSameAs(delegateService.schema());
        assertThat(filteredService.typeRegistry()).isSameAs(delegateService.typeRegistry());
    }

    @Test
    public void testFilterThatRemovesAllOperations() {
        // Create a filter that removes all operations
        Predicate<ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>> filter = operation -> false;
        FilteredService filteredService = new FilteredService(delegateService, filter);

        // Verify that getAllOperations returns an empty list
        List<Operation<?, ?>> filteredOperations = filteredService.getAllOperations();
        assertThat(filteredOperations).isEmpty();

        // Verify that getOperation throws for any operation
        assertThatThrownBy(() -> filteredService.getOperation("GetItem"))
                .isInstanceOf(UnknownOperationException.class);
    }

    @Test
    public void testOperationInvocation() {
        // Create a filtered service
        Predicate<ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>> filter =
                OperationFilters.allowList(Set.of("GetItem"));
        FilteredService filteredService = new FilteredService(delegateService, filter);

        // Get the operation and verify we can invoke it
        Operation<MockStruct, MockStruct> op = filteredService.getOperation("GetItem");
        var mockInput = new MockStruct();

        // Verify the result is the same.
        assertSame(mockInput, op.function().apply(mockInput, null));
    }
}
