/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.vertx;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

/**
 * rpcv2Cbor-flavored service fixture with one no-input/no-output {@code Ping}
 * operation. The bridge mounts it at {@code POST /service/Ping/operation/Ping}.
 */
final class PingFixture {

    private PingFixture() {}

    static final ShapeId SERVICE_ID = ShapeId.from("test#Ping");
    static final ShapeId PING_OP_ID = ShapeId.from("test#Ping");

    static PingService pingService() {
        return new PingService();
    }

    /** Empty struct for the rpcv2-cbor fixture. */
    static final class EmptyCbor implements SerializableStruct {
        static final EmptyCbor INSTANCE = new EmptyCbor();
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("test#PingEmpty")).build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            // empty
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        static final class Builder implements ShapeBuilder<EmptyCbor> {
            @Override
            public EmptyCbor build() {
                return INSTANCE;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            @Override
            public ShapeBuilder<EmptyCbor> deserialize(ShapeDeserializer d) {
                d.readStruct(SCHEMA, this, (state, member, des) -> {});
                return this;
            }
        }
    }

    static final class PingApiOperation implements ApiOperation<EmptyCbor, EmptyCbor> {
        static final PingApiOperation INSTANCE = new PingApiOperation();

        private static final Schema OPERATION_SCHEMA = Schema.createOperation(PING_OP_ID);

        @Override
        public ShapeBuilder<EmptyCbor> inputBuilder() {
            return new EmptyCbor.Builder();
        }

        @Override
        public ShapeBuilder<EmptyCbor> outputBuilder() {
            return new EmptyCbor.Builder();
        }

        @Override
        public Schema schema() {
            return OPERATION_SCHEMA;
        }

        @Override
        public Schema inputSchema() {
            return EmptyCbor.SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            return EmptyCbor.SCHEMA;
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

    static final class PingService implements Service {

        private static final Schema SCHEMA =
                Schema.createService(SERVICE_ID, (Trait) Rpcv2CborTrait.builder().build());

        final AtomicReference<String> lastInvoked = new AtomicReference<>();

        private final Operation<EmptyCbor, EmptyCbor> ping = Operation.of(
                "Ping",
                (input, ctx) -> {
                    lastInvoked.set("Ping");
                    return EmptyCbor.INSTANCE;
                },
                PingApiOperation.INSTANCE,
                this);

        @Override
        @SuppressWarnings("unchecked")
        public <I extends SerializableStruct, O extends SerializableStruct> Operation<I, O> getOperation(
                String operationName
        ) {
            if ("Ping".equals(operationName)) {
                return (Operation<I, O>) ping;
            }
            return null;
        }

        @Override
        public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
            return List.of(ping);
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
