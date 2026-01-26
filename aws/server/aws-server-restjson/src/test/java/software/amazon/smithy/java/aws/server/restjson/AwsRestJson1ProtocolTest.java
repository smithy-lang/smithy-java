/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpTrait;

public class AwsRestJson1ProtocolTest {

    @Test
    void shouldInitializeWithHttpOperations() {
        Service service = createServiceWithHttpOperations();
        assertDoesNotThrow(() -> new AwsRestJson1Protocol(List.of(service)));
    }

    @Test
    void shouldHandleOperationsWithoutHttpTraits() {
        Service service = createServiceWithoutHttpOperations();
        assertDoesNotThrow(() -> new AwsRestJson1Protocol(List.of(service)));
    }

    @Test
    void shouldHandleMixedOperations() {
        Service service = createServiceWithMixedOperations();
        AwsRestJson1Protocol protocol = assertDoesNotThrow(() -> new AwsRestJson1Protocol(List.of(service)));
        assertThat(protocol, notNullValue());
    }

    @Test
    void shouldHandleEmptyService() {
        Service service = createEmptyService();
        assertDoesNotThrow(() -> new AwsRestJson1Protocol(List.of(service)));
    }

    @Test
    void shouldHandleMultipleServices() {
        Service httpService = createServiceWithHttpOperations();
        Service nonHttpService = createServiceWithoutHttpOperations();
        assertDoesNotThrow(() -> new AwsRestJson1Protocol(List.of(httpService, nonHttpService)));
    }

    @Test
    void shouldReturnCorrectProtocolId() {
        Service service = createServiceWithHttpOperations();
        AwsRestJson1Protocol protocol = new AwsRestJson1Protocol(List.of(service));
        assertThat(protocol.getProtocolId(), equalTo(RestJson1Trait.ID));
    }

    private Service createServiceWithHttpOperations() {
        Operation<?, ?> getOp = createOperationWithHttpTrait("GetItem", "GET", "/items/{id}");
        Operation<?, ?> postOp = createOperationWithHttpTrait("CreateItem", "POST", "/items");
        return new TestService(List.of(getOp, postOp));
    }

    private Service createServiceWithoutHttpOperations() {
        Operation<?, ?> rpcOp = createOperationWithoutHttpTrait("RpcOperation");
        return new TestService(List.of(rpcOp));
    }

    private Service createServiceWithMixedOperations() {
        Operation<?, ?> httpOp = createOperationWithHttpTrait("GetItem", "GET", "/items/{id}");
        Operation<?, ?> nonHttpOp = createOperationWithoutHttpTrait("RpcOperation");
        return new TestService(List.of(httpOp, nonHttpOp));
    }

    private Service createEmptyService() {
        return new TestService(List.of());
    }

    private Operation<?, ?> createOperationWithHttpTrait(String name, String method, String uri) {
        HttpTrait httpTrait = HttpTrait.builder()
                .method(method)
                .uri(UriPattern.parse(uri))
                .build();
        Schema operationSchema = Schema.structureBuilder(ShapeId.from("test#" + name), httpTrait).build();
        return Operation.of(name,
                (input, ctx) -> new TestOutput(),
                new TestApiOperation(operationSchema),
                new TestService(List.of()));
    }

    private Operation<?, ?> createOperationWithoutHttpTrait(String name) {
        Schema operationSchema = Schema.structureBuilder(ShapeId.from("test#" + name)).build();
        return Operation.of(name,
                (input, ctx) -> new TestOutput(),
                new TestApiOperation(operationSchema),
                new TestService(List.of()));
    }

    private static class TestService implements Service {
        private final List<Operation<?, ?>> operations;

        TestService(List<Operation<?, ?>> operations) {
            this.operations = operations;
        }

        @Override
        public <I extends SerializableStruct,
                O extends SerializableStruct> Operation<I, O> getOperation(String operationName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
            return operations;
        }

        @Override
        public Schema schema() {
            return Schema.structureBuilder(ShapeId.from("test#TestService")).build();
        }

        @Override
        public TypeRegistry typeRegistry() {
            return TypeRegistry.builder().build();
        }

        @Override
        public SchemaIndex schemaIndex() {
            throw new UnsupportedOperationException();
        }
    }

    private static class TestApiOperation implements ApiOperation {
        private final Schema operationSchema;

        TestApiOperation(Schema operationSchema) {
            this.operationSchema = operationSchema;
        }

        @Override
        public ShapeBuilder<? extends SerializableStruct> inputBuilder() {
            return new TestBuilder();
        }

        @Override
        public ShapeBuilder<? extends SerializableStruct> outputBuilder() {
            return new TestBuilder();
        }

        @Override
        public Schema schema() {
            return operationSchema;
        }

        @Override
        public Schema inputSchema() {
            return Schema.structureBuilder(ShapeId.from("test#TestInput")).build();
        }

        @Override
        public Schema outputSchema() {
            return Schema.structureBuilder(ShapeId.from("test#TestOutput")).build();
        }

        @Override
        public TypeRegistry errorRegistry() {
            return TypeRegistry.builder().build();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            return List.of();
        }

        @Override
        public ApiService service() {
            return () -> Schema.structureBuilder(ShapeId.from("test#TestService")).build();
        }
    }

    private static class TestOutput implements SerializableStruct {
        @Override
        public Schema schema() {
            return Schema.structureBuilder(ShapeId.from("test#TestOutput")).build();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    private static class TestBuilder implements ShapeBuilder<TestOutput> {
        @Override
        public TestOutput build() {
            return new TestOutput();
        }

        @Override
        public ShapeBuilder<TestOutput> deserialize(ShapeDeserializer decoder) {
            return this;
        }

        @Override
        public Schema schema() {
            return Schema.structureBuilder(ShapeId.from("test#TestOutput")).build();
        }
    }
}
