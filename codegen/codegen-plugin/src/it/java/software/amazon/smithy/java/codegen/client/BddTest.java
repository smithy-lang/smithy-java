/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.bddTest.client.ServiceWithEndpointBddClient;
import smithy.java.codegen.server.bddTest.client.ServiceWithEndpointRuleSetClient;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.client.rulesengine.EndpointRulesPlugin;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

public class BddTest {
    @Test
    public void testServiceWithEndpointBddResolution() {
        var client = ServiceWithEndpointBddClient.builder()
                .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
                .build();

        var resolver = client.config().endpointResolver();

        var params1 = createParams("us-east-1", false);
        var endpoint1 = resolver.resolveEndpoint(params1);
        assertNotNull(endpoint1);
        assertEquals("https://service.us-east-1.amazonaws.com", endpoint1.uri().toString());

        var params2 = createParams("us-east-2", true);
        var endpoint2 = resolver.resolveEndpoint(params2);
        assertNotNull(endpoint2);
        assertEquals("https://service-fips.us-east-2.amazonaws.com", endpoint2.uri().toString());
    }

    @Test
    public void testEndpointRuleSetResolution() {
        var client = ServiceWithEndpointRuleSetClient.builder()
                .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
                .build();

        var resolver = client.config().endpointResolver();

        var params = createParams("us-east-1", false);
        var endpoint = resolver.resolveEndpoint(params);
        assertNotNull(endpoint);
        assertEquals("https://us-east-1.amazonaws.com", endpoint.uri().toString());
    }

    private EndpointResolverParams createParams(String Region, Boolean UseFips) {
        Map<String, Object> endpointParams = new HashMap<>();
        if (Region != null) {
            endpointParams.put("Region", Region);
        }
        if (UseFips != null) {
            endpointParams.put("UseFips", UseFips);
        }
        Context context = Context.create();
        Context fullContext = context.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, endpointParams);

        TestOperation operation = new TestOperation();
        TestInput input = new TestInput();

        return EndpointResolverParams.builder().operation(operation).inputValue(input).context(fullContext).build();
    }

    private static final Schema INPUT_SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#I")).build();

    private static class TestOperation implements ApiOperation<TestInput, TestInput> {
        @Override
        public ShapeBuilder<TestInput> inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<TestInput> outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return Schema.createOperation(ShapeId.from("smithy.example#Foo"));
        }

        @Override
        public Schema inputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public TypeRegistry errorRegistry() {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }
    }

    private static class TestInput implements SerializableStruct {
        @Override
        public Schema schema() {
            return INPUT_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
