/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicOperation;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A hand-built (non-generated) Smithy {@link Service} whose operations read and mutate a shared
 * {@link InspectorState}. Registered into an MCP server so each operation becomes an MCP tool.
 *
 * <p>Follows the {@code ProxyService} pattern: the control model is assembled at runtime, every
 * operation is a document-backed {@link DynamicOperation}, and each handler returns a
 * {@link StructDocument}. No code generation is required.
 */
public final class InspectorService implements Service {

    static final ShapeId SERVICE_ID = ShapeId.from("software.amazon.smithy.java.client.inspector#SdkInspector");

    private final InspectorState state;
    private final SchemaConverter schemaConverter;
    private final Map<String, Operation<StructDocument, StructDocument>> operations = new HashMap<>();
    private final List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> allOperations =
            new ArrayList<>();
    private final Schema schema;
    private final SchemaIndex schemaIndex;
    private final InspectorHandlers handlers;

    public InspectorService(InspectorState state) {
        this.state = state;
        var model = Model.assembler(InspectorService.class.getClassLoader())
                .discoverModels(InspectorService.class.getClassLoader())
                .assemble()
                .unwrap();
        var service = model.expectShape(SERVICE_ID, ServiceShape.class);
        this.schemaConverter = new SchemaConverter(model);
        this.handlers = new InspectorHandlers(state);

        for (var opShape : TopDownIndex.of(model).getContainedOperations(service.getId())) {
            var name = opShape.getId().getName();
            var apiOperation = DynamicOperation.create(
                    opShape,
                    schemaConverter,
                    model,
                    service,
                    TypeRegistry.EMPTY,
                    (e, rb) -> {});
            var outputSchema = schemaConverter.getSchema(model.expectShape(opShape.getOutputShape()));
            BiFunction<StructDocument, RequestContext, StructDocument> fn =
                    new Handler(name, outputSchema, handlers);
            var operation = Operation.of(name, fn, apiOperation, this);
            operations.put(name, operation);
            allOperations.add(operation);
        }
        this.schema = schemaConverter.getSchema(service);
        this.schemaIndex = schemaConverter.getSchemaIndex();
    }

    public InspectorState state() {
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends SerializableStruct, O extends SerializableStruct> Operation<I, O> getOperation(String name) {
        return (Operation<I, O>) operations.get(name);
    }

    @Override
    public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
        return allOperations;
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return TypeRegistry.EMPTY;
    }

    @Override
    public SchemaIndex schemaIndex() {
        return schemaIndex;
    }

    /** Dispatches a tool call to the matching handler and wraps the result as a StructDocument. */
    private record Handler(String operation, Schema outputSchema, InspectorHandlers handlers)
            implements BiFunction<StructDocument, RequestContext, StructDocument> {
        @Override
        public StructDocument apply(StructDocument input, RequestContext context) {
            Map<String, Document> result = handlers.handle(operation, input);
            return StructDocument.of(outputSchema, Document.of(result), SERVICE_ID);
        }
    }
}
