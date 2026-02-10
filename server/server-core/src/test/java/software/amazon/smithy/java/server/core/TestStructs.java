/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

public class TestStructs {
    public static abstract class TestInput implements SerializableStruct {
        @Override
        public Schema schema() {
            return null;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {

        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    public static class TestOutput implements SerializableStruct {
        @Override
        public Schema schema() {
            return null;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {

        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    public static class TestService implements Service {
        @Override
        public <I extends SerializableStruct,
                O extends SerializableStruct> Operation<I, O> getOperation(String operationName) {
            return null;
        }

        @Override
        public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
            return List.of();
        }

        @Override
        public Schema schema() {
            return null;
        }

        @Override
        public TypeRegistry typeRegistry() {
            return null;
        }

        @Override
        public SchemaIndex schemaIndex() {
            return null;
        }
    }

    public static class TestApiOperation implements ApiOperation {
        ApiService apiService = null;

        public TestApiOperation(ApiService apiService) {
            this.apiService = apiService;
        }

        public TestApiOperation() {}

        @Override
        public ShapeBuilder<TestInput> inputBuilder() {
            return null;
        }

        @Override
        public ShapeBuilder<TestOutput> outputBuilder() {
            return null;
        }

        @Override
        public Schema schema() {
            return null;
        }

        @Override
        public Schema inputSchema() {
            return null;
        }

        @Override
        public Schema outputSchema() {
            return null;
        }

        @Override
        public TypeRegistry errorRegistry() {
            return null;
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
            return apiService;
        }
    }

    public static class TestModifiableHttpHeaders implements ModifiableHttpHeaders {
        Map<String, List<String>> headers = new HashMap<>();

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, List.of(value));
        }

        @Override
        public void addHeader(String name, List<String> values) {
            headers.put(name, values);
        }

        @Override
        public void removeHeader(String name) {
            headers.remove(name);
        }

        @Override
        public void clear() {
            headers.clear();
        }

        @Override
        public List<String> allValues(String name) {
            return headers.getOrDefault(name, List.of());
        }

        @Override
        public int size() {
            return headers.size();
        }

        @Override
        public Map<String, List<String>> map() {
            return headers;
        }

        @Override
        public Iterator<Map.Entry<String, List<String>>> iterator() {
            return headers.entrySet().iterator();
        }
    }

    public static class TestServerProtocol extends ServerProtocol {

        protected TestServerProtocol(List<Service> services) {
            super(services);
        }

        @Override
        public ShapeId getProtocolId() {
            return null;
        }

        @Override
        public ServiceProtocolResolutionResult resolveOperation(
                ServiceProtocolResolutionRequest request,
                List<Service> candidates
        ) {
            return null;
        }

        @Override
        public CompletableFuture<Void> deserializeInput(Job job) {
            return null;
        }

        @Override
        protected CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
            return null;
        }
    }
}
