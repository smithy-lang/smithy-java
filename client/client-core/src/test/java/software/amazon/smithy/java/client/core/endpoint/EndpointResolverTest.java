/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.endpoint;

import java.time.Instant;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.HostLabelTrait;

public class EndpointResolverTest {
    private static final Endpoint TEST_ENDPOINT = Endpoint.builder().uri("https://example.com").build();

    @Test
    public void returnsStaticHostIgnoringHostLabel() {
        EndpointResolver resolver = EndpointResolver.staticHost(TEST_ENDPOINT);
        Endpoint endpoint = resolver.resolveEndpoint(
                EndpointResolverParams.builder()
                        .operation(new TestOperationTemplatePrefix())
                        .inputValue(new EndpointInput("name", "bar", "baz"))
                        .build())
                .join();

        MatcherAssert.assertThat(
                endpoint.uri().toString(),
                Matchers.equalTo("https://example.com"));
    }

    @Test
    public void returnsStaticEndpointWithStaticPrefix() {
        EndpointResolver resolver = EndpointResolver.staticEndpoint(TEST_ENDPOINT);
        Endpoint endpoint = resolver.resolveEndpoint(
                EndpointResolverParams.builder()
                        .operation(new TestOperationStaticPrefix())
                        .inputValue(new EndpointInput("name", "bar", "baz"))
                        .build())
                .join();

        MatcherAssert.assertThat(
                endpoint.uri().toString(),
                Matchers.equalTo("https://foo.bar.example.com"));
    }

    @Test
    public void returnsStaticEndpointWithTemplatedPrefix() {
        EndpointResolver resolver = EndpointResolver.staticEndpoint(TEST_ENDPOINT);
        Endpoint endpoint = resolver.resolveEndpoint(
                EndpointResolverParams.builder()
                        .operation(new TestOperationTemplatePrefix())
                        .inputValue(new EndpointInput("name", "bar", "baz"))
                        .build())
                .join();

        MatcherAssert.assertThat(
                endpoint.uri().toString(),
                Matchers.equalTo("https://bar-baz.foo.example.com"));
    }

    private record EndpointInput(String name, String labelA, String labelB) implements SerializableStruct {

        public static final ShapeId ID = ShapeId.from("smithy.example#EndpointInput");
        public static final Schema SCHEMA = Schema.structureBuilder(ID)
                .putMember("name", PreludeSchemas.STRING)
                .putMember("labelA", PreludeSchemas.STRING, new HostLabelTrait())
                .putMember("labelB", PreludeSchemas.STRING, new HostLabelTrait())
                .putMember("integerVal", PreludeSchemas.INTEGER)
                .putMember("timestampVal", PreludeSchemas.TIMESTAMP)
                .build();
        public static final Schema SCHEMA_NAME = SCHEMA.member("name");
        public static final Schema SCHEMA_LABEL_A = SCHEMA.member("labelA");
        public static final Schema SCHEMA_LABEL_B = SCHEMA.member("labelB");
        public static final Schema SCHEMA_INTEGER_VAL = SCHEMA.member("integerVal");
        public static final Schema SCHEMA_TIMESTAMP_VAL = SCHEMA.member("timestampVal");

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_NAME, name);
            serializer.writeString(SCHEMA_LABEL_A, labelA);
            serializer.writeString(SCHEMA_LABEL_B, labelB);
            serializer.writeInteger(SCHEMA_INTEGER_VAL, 2);
            serializer.writeTimestamp(SCHEMA_TIMESTAMP_VAL, Instant.EPOCH);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestOperationTemplatePrefix implements
            ApiOperation<SerializableStruct, SerializableStruct> {
        private static final Schema SCHEMA = Schema.createOperation(
                ShapeId.from("foo.bar#operationA"),
                EndpointTrait.builder()
                        .hostPrefix("{labelA}-{labelB}.foo.")
                        .sourceLocation(SourceLocation.NONE)
                        .build());

        @Override
        public ShapeBuilder<SerializableStruct> inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<SerializableStruct> outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public Schema inputSchema() {
            return EndpointInput.SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeRegistry errorRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApiService service() {
            return null;
        }
    }

    private static final class TestOperationStaticPrefix implements
            ApiOperation<SerializableStruct, SerializableStruct> {
        private static final Schema SCHEMA = Schema.createOperation(
                ShapeId.from("foo.bar#operationA"),
                EndpointTrait.builder()
                        .hostPrefix("foo.bar.")
                        .sourceLocation(SourceLocation.NONE)
                        .build());

        @Override
        public ShapeBuilder<SerializableStruct> inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<SerializableStruct> outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public Schema inputSchema() {
            return EndpointInput.SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApiService service() {
            return null;
        }

        @Override
        public TypeRegistry errorRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            throw new UnsupportedOperationException();
        }
    }
}
