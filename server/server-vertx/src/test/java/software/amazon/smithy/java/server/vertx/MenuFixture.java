/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.vertx;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import software.amazon.smithy.model.traits.Trait;

/**
 * Hand-rolled test fixture: a tiny "Menu" service with one
 * {@code GET /menu} operation. Avoids depending on smithy code
 * generation in the Vert.x server module's tests.
 */
final class MenuFixture {

    private MenuFixture() {}

    static final ShapeId SERVICE_ID = ShapeId.from("test#Menu");
    static final ShapeId GET_MENU_ID = ShapeId.from("test#GetMenu");
    static final ShapeId GET_ORDER_ID = ShapeId.from("test#GetOrder");
    static final ShapeId PUT_ORDER_ID = ShapeId.from("test#PutOrder");

    static MenuService menuService() {
        return new MenuService();
    }

    /** Empty struct used as input/output for the Menu service's operations. */
    static final class EmptyStruct implements SerializableStruct {
        static final EmptyStruct INSTANCE = new EmptyStruct();
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("test#Empty")).build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            // no members
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        static final class Builder implements ShapeBuilder<EmptyStruct> {
            @Override
            public EmptyStruct build() {
                return INSTANCE;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            @Override
            public ShapeBuilder<EmptyStruct> deserialize(ShapeDeserializer d) {
                d.readStruct(SCHEMA, this, (state, member, des) -> {});
                return this;
            }
        }
    }

    /**
     * Lightweight helper: an {@link ApiOperation} with an empty input
     * and empty output, parameterized only by id and HTTP trait. Used
     * to add multiple operations to the fixture without writing the
     * full ApiOperation boilerplate per op.
     */
    static final class StubApiOperation implements ApiOperation<EmptyStruct, EmptyStruct> {

        private final Schema operationSchema;

        StubApiOperation(ShapeId id, HttpTrait http) {
            this.operationSchema = Schema.createOperation(id, (Trait) http);
        }

        @Override
        public ShapeBuilder<EmptyStruct> inputBuilder() {
            return new EmptyStruct.Builder();
        }

        @Override
        public ShapeBuilder<EmptyStruct> outputBuilder() {
            return new EmptyStruct.Builder();
        }

        @Override
        public Schema schema() {
            return operationSchema;
        }

        @Override
        public Schema inputSchema() {
            return EmptyStruct.SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            return EmptyStruct.SCHEMA;
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
        public List<Schema> errorSchemas() {
            return List.of();
        }

        @Override
        public ApiService service() {
            return null;
        }
    }

    private static HttpTrait http(String method, String uri) {
        return HttpTrait.builder()
                .method(method)
                .uri(UriPattern.parse(uri))
                .code(200)
                .build();
    }

    static final class MenuService implements Service {

        private static final Schema SCHEMA = Schema.createService(SERVICE_ID, (Trait) RestJson1Trait.builder().build());

        // Tracks which operations were invoked for tests that care.
        final AtomicReference<String> lastInvoked =
                new AtomicReference<>();

        private final Operation<EmptyStruct, EmptyStruct> getMenu = Operation.of(
                "GetMenu",
                (input, ctx) -> {
                    lastInvoked.set("GetMenu");
                    return EmptyStruct.INSTANCE;
                },
                new StubApiOperation(GET_MENU_ID, http("GET", "/menu")),
                this);

        private final Operation<EmptyStruct, EmptyStruct> getOrder = Operation.of(
                "GetOrder",
                (input, ctx) -> {
                    lastInvoked.set("GetOrder");
                    return EmptyStruct.INSTANCE;
                },
                new StubApiOperation(GET_ORDER_ID, http("GET", "/order/{id}")),
                this);

        private final Operation<EmptyStruct, EmptyStruct> putOrder = Operation.of(
                "PutOrder",
                (input, ctx) -> {
                    lastInvoked.set("PutOrder");
                    return EmptyStruct.INSTANCE;
                },
                new StubApiOperation(PUT_ORDER_ID, http("PUT", "/order")),
                this);

        @Override
        @SuppressWarnings("unchecked")
        public <I extends SerializableStruct, O extends SerializableStruct> Operation<I, O> getOperation(
                String operationName
        ) {
            return (Operation<I, O>) switch (operationName) {
                case "GetMenu" -> getMenu;
                case "GetOrder" -> getOrder;
                case "PutOrder" -> putOrder;
                default -> null;
            };
        }

        @Override
        public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
            return List.of(getMenu, getOrder, putOrder);
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public TypeRegistry typeRegistry() {
            return TypeRegistry.builder().build();
        }

        @Override
        public SchemaIndex schemaIndex() {
            return SchemaIndex.compose();
        }
    }
}
