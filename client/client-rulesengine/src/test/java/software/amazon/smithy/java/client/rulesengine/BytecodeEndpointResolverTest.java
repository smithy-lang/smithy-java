/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

class BytecodeEndpointResolverTest {

    @Test
    void testSimpleEndpointResolution() throws Exception {
        // Create a simple bytecode that returns a fixed endpoint
        // BDD: root -> result 0 (always returns result 0)
        Bytecode bytecode = new Bytecode(
                new byte[] {
                        Opcodes.LOAD_CONST,
                        0,
                        Opcodes.RETURN_ENDPOINT,
                        0
                },
                new int[0], // No conditions
                new int[] {0}, // One result at offset 0
                new RegisterDefinition[] {
                        new RegisterDefinition("region", false, null, null, false),
                        new RegisterDefinition("bucket", false, null, null, false)
                },
                new Object[] {"https://example.com"},
                new RulesFunction[0],
                new int[] {-1, 100_000_000, -1}, // Terminal node that returns result 0
                100_000_000 // Root points to result 0
        );

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bytecode,
                List.of(),
                Map.of());

        EndpointResolverParams params = createParams("us-east-1", "my-bucket");

        CompletableFuture<Endpoint> future = resolver.resolveEndpoint(params);
        Endpoint endpoint = future.get();

        assertNotNull(endpoint);
        assertEquals("https://example.com", endpoint.uri().toString());
    }

    @Test
    void testEndpointWithBuiltinProvider() throws Exception {
        // Create bytecode that uses a builtin
        // The BDD just returns result 0 which uses the builtin value
        Bytecode bytecode = new Bytecode(
                new byte[] {
                        Opcodes.LOAD_REGISTER,
                        0, // Load the endpoint register
                        Opcodes.RETURN_ENDPOINT,
                        0
                },
                new int[0], // No conditions
                new int[] {0}, // One result at offset 0
                new RegisterDefinition[] {
                        new RegisterDefinition("endpoint", false, null, "SDK::Endpoint", false),
                        new RegisterDefinition("region", false, null, null, false)
                },
                new Object[0],
                new RulesFunction[0],
                new int[] {-1, 100_000_000, -1}, // Terminal node that returns result 0
                100_000_000 // Root points to result 0
        );

        Map<String, Function<Context, Object>> builtinProviders = Map.of(
                "SDK::Endpoint",
                ctx -> {
                    Endpoint custom = ctx.get(ClientContext.CUSTOM_ENDPOINT);
                    return custom != null ? custom.uri().toString() : null;
                });

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bytecode,
                List.of(),
                builtinProviders);

        // Test with custom endpoint
        Context context = Context.create()
                .put(
                        ClientContext.CUSTOM_ENDPOINT,
                        Endpoint.builder().uri("https://custom.example.com").build());

        EndpointResolverParams params = createParams("us-west-2", "bucket", context);

        Endpoint endpoint = resolver.resolveEndpoint(params).get();
        assertEquals("https://custom.example.com", endpoint.uri().toString());
    }

    @Test
    void testNoMatchReturnsNull() throws Exception {
        // Create bytecode that returns no match (result 0 is NoMatchRule)
        Bytecode bytecode = new Bytecode(
                new byte[] {
                        Opcodes.LOAD_CONST,
                        0,
                        Opcodes.RETURN_VALUE
                },
                new int[0], // No conditions
                new int[] {0}, // One result at offset 0 (NoMatchRule)
                new RegisterDefinition[0],
                new Object[] {null},
                new RulesFunction[0],
                new int[] {-1, -1, -1}, // Terminal node
                -1 // Root points to FALSE (no match)
        );

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bytecode,
                List.of(),
                Map.of());

        EndpointResolverParams params = createParams("us-east-1", "bucket");

        Endpoint endpoint = resolver.resolveEndpoint(params).get();
        assertNull(endpoint);
    }

    @Test
    void testConditionalEndpoint() throws Exception {
        // Create bytecode with a condition that checks if region is set
        // Condition 0: isSet(region)
        // Result 0: no match (returns null)
        // Result 1: return endpoint

        // Condition bytecode
        byte[] conditionBytecode = new byte[] {
                Opcodes.TEST_REGISTER_ISSET,
                0, // Test if register 0 (region) is set
                Opcodes.RETURN_VALUE
        };

        // Result 0 bytecode (no match - returns null)
        byte[] noMatchBytecode = new byte[] {
                Opcodes.LOAD_CONST,
                0, // Load null
                Opcodes.RETURN_VALUE // Return null (not RETURN_ENDPOINT)
        };

        // Result 1 bytecode (endpoint)
        byte[] endpointBytecode = new byte[] {
                Opcodes.LOAD_CONST,
                1, // Load URL string
                Opcodes.RETURN_ENDPOINT,
                0
        };

        // Combine bytecode
        byte[] bytecode = new byte[conditionBytecode.length + noMatchBytecode.length + endpointBytecode.length];
        System.arraycopy(conditionBytecode, 0, bytecode, 0, conditionBytecode.length);
        System.arraycopy(noMatchBytecode, 0, bytecode, conditionBytecode.length, noMatchBytecode.length);
        System.arraycopy(endpointBytecode,
                0,
                bytecode,
                conditionBytecode.length + noMatchBytecode.length,
                endpointBytecode.length);

        Bytecode bc = new Bytecode(
                bytecode,
                new int[] {0}, // Condition at offset 0
                new int[] {
                        conditionBytecode.length, // Result 0 at offset after condition
                        conditionBytecode.length + noMatchBytecode.length // Result 1 at offset after result 0
                },
                new RegisterDefinition[] {
                        new RegisterDefinition("region", false, null, null, false),
                        new RegisterDefinition("bucket", false, null, null, false)
                },
                new Object[] {null, "https://example.com"}, // null for no-match, URL for endpoint
                new RulesFunction[0],
                // BDD nodes: [varIdx, highRef, lowRef]
                // Node 0: terminal
                // Node 1: condition 0, high=result 1, low=result 0
                new int[] {
                        -1,
                        -1,
                        -1, // Node 0: terminal
                        0,
                        100_000_001,
                        100_000_000 // Node 1: if condition 0, then result 1, else result 0
                },
                2 // Root points to node 1 (1-based indexing)
        );

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bc,
                List.of(),
                Map.of());

        // Test with region provided
        EndpointResolverParams params = createParams("us-east-1", "bucket");
        Endpoint endpoint = resolver.resolveEndpoint(params).get();
        assertNotNull(endpoint);
        assertEquals("https://example.com", endpoint.uri().toString());

        // Test without region
        params = createParams(null, "bucket");
        endpoint = resolver.resolveEndpoint(params).get();
        assertNull(endpoint); // Should return no match (result 0)
    }

    @Test
    void testErrorPropagation() {
        // Create bytecode that throws an error
        Bytecode bytecode = new Bytecode(
                new byte[] {
                        Opcodes.LOAD_CONST,
                        0,
                        Opcodes.RETURN_ERROR
                },
                new int[0], // No conditions
                new int[] {0}, // One result at offset 0
                new RegisterDefinition[0],
                new Object[] {"Test error"},
                new RulesFunction[0],
                new int[] {-1, 100_000_000, -1}, // Terminal node that returns result 0
                100_000_000 // Root points to result 0
        );

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bytecode,
                List.of(),
                Map.of());

        EndpointResolverParams params = createParams("us-east-1", "bucket");

        CompletableFuture<Endpoint> future = resolver.resolveEndpoint(params);

        Exception exception = assertThrows(Exception.class, future::get);
        assertInstanceOf(RulesEvaluationError.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Test error"));
    }

    @Test
    void testWithRulesExtension() throws Exception {
        Bytecode bytecode = new Bytecode(
                new byte[] {
                        Opcodes.LOAD_CONST,
                        0,
                        Opcodes.RETURN_ENDPOINT,
                        0
                },
                new int[0],
                new int[] {0},
                new RegisterDefinition[] {
                        new RegisterDefinition("region", false, null, null, false)
                },
                new Object[] {"https://example.com"},
                new RulesFunction[0],
                new int[] {-1, 100_000_000, -1},
                100_000_000);

        TestRulesExtension extension = new TestRulesExtension();

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bytecode,
                List.of(extension),
                Map.of());

        EndpointResolverParams params = createParams("us-east-1", "bucket");

        Endpoint endpoint = resolver.resolveEndpoint(params).get();

        assertTrue(extension.wasCalled);
        assertNotNull(endpoint);
    }

    @Test
    void testMissingRequiredParameter() {
        // Create bytecode with required parameter
        RegisterDefinition[] defs = {
                new RegisterDefinition("region", true, null, null, false),
                new RegisterDefinition("bucket", true, null, null, false)
        };

        Bytecode bytecode = new Bytecode(
                new byte[] {Opcodes.LOAD_CONST, 0, Opcodes.RETURN_ENDPOINT, 0},
                new int[0],
                new int[] {0},
                defs,
                new Object[] {"https://example.com"},
                new RulesFunction[0],
                new int[] {-1, 100_000_000, -1},
                100_000_000);

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bytecode,
                List.of(),
                Map.of());

        // Only provide region, not bucket
        EndpointResolverParams params = createParams("us-east-1", null);

        CompletableFuture<Endpoint> future = resolver.resolveEndpoint(params);

        Exception exception = assertThrows(Exception.class, future::get);
        assertInstanceOf(RulesEvaluationError.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("bucket"));
    }

    @Test
    void testParameterWithDefaultValue() throws Exception {
        // Create bytecode with a parameter that has a default value
        RegisterDefinition[] defs = {
                new RegisterDefinition("region", true, "us-west-2", null, false), // Has default
                new RegisterDefinition("bucket", false, null, null, false)
        };

        Bytecode bytecode = new Bytecode(
                new byte[] {
                        Opcodes.LOAD_REGISTER,
                        0, // Load region
                        Opcodes.LOAD_CONST,
                        0, // Load "/"
                        Opcodes.LOAD_REGISTER,
                        1, // Load bucket
                        Opcodes.RESOLVE_TEMPLATE,
                        3, // Concatenate
                        Opcodes.RETURN_ENDPOINT,
                        0
                },
                new int[0],
                new int[] {0},
                defs,
                new Object[] {"/"},
                new RulesFunction[0],
                new int[] {-1, 100_000_000, -1},
                100_000_000);

        BytecodeEndpointResolver resolver = new BytecodeEndpointResolver(
                bytecode,
                List.of(),
                Map.of());

        // Don't provide region, should use default
        EndpointResolverParams params = createParams(null, "my-bucket");

        Endpoint endpoint = resolver.resolveEndpoint(params).get();
        assertNotNull(endpoint);
        assertEquals("us-west-2/my-bucket", endpoint.uri().toString());
    }

    // Helper methods

    private EndpointResolverParams createParams(String region, String bucket) {
        return createParams(region, bucket, Context.create());
    }

    private EndpointResolverParams createParams(String region, String bucket, Context context) {
        Map<String, Object> endpointParams = new HashMap<>();
        if (region != null) {
            endpointParams.put("region", region);
        }
        if (bucket != null) {
            endpointParams.put("bucket", bucket);
        }

        Context fullContext = context.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, endpointParams);

        TestOperation operation = new TestOperation();
        TestInput input = new TestInput();

        return EndpointResolverParams.builder().operation(operation).inputValue(input).context(fullContext).build();
    }

    private static class TestRulesExtension implements RulesExtension {
        boolean wasCalled = false;

        @Override
        public void extractEndpointProperties(
                Endpoint.Builder builder,
                Context context,
                Map<String, Object> properties,
                Map<String, List<String>> headers
        ) {
            wasCalled = true;
        }
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
