/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.dynamicclient.DynamicOperation;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Resolves the {@link ApiOperation} a serde benchmark drives, in one of two
 * modes selected by the {@code smithy-java.benchmark.client} system property:
 *
 * <ul>
 *   <li>{@code codegen} (default) — the codegen-generated {@code ApiOperation}
 *       singleton, resolved by reflection from the protocol's generated
 *       package. This is what ships and what the published numbers reflect.</li>
 *   <li>{@code dynamic} — a {@link DynamicOperation} built at runtime from the
 *       assembled Smithy model via {@link SchemaConverter}, with a
 *       {@code StructDocument} input. Lets us measure the dynamic client on the
 *       exact same payloads and protocol entry points.</li>
 * </ul>
 *
 * <p>Both modes return an {@code ApiOperation} whose {@code inputBuilder()} /
 * {@code outputBuilder()} accept the same schema-guided document deserialization
 * the benchmarks already use, so the only difference is operation construction —
 * the {@code protocol.createRequest} / {@code deserializeResponse} calls in the
 * benchmark loop are identical.
 */
final class BenchmarkOperations {

    /** System property selecting codegen (default) vs dynamic operation build. */
    static final String CLIENT_MODE_PROPERTY = "smithy-java.benchmark.client";

    private BenchmarkOperations() {}

    static boolean isDynamic() {
        return "dynamic".equalsIgnoreCase(System.getProperty(CLIENT_MODE_PROPERTY, "codegen"));
    }

    /** A short label for the active mode, for result metadata / logging. */
    static String modeLabel() {
        return isDynamic() ? "dynamic" : "codegen";
    }

    /**
     * Resolve the operation for a benchmark test case.
     *
     * @param opShape          the operation shape from the benchmark model
     * @param generatedPackage codegen package for the protocol projection
     * @param serviceId        the service the operation is bound to (needed by
     *                         the dynamic path to convert the service schema)
     */
    static ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> resolve(
            OperationShape opShape,
            String generatedPackage,
            ShapeId serviceId
    ) {
        if (isDynamic()) {
            return dynamicOperation(opShape, serviceId);
        }
        return SerializeState.resolveOperation(generatedPackage, opShape.getId().getName());
    }

    private static ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> dynamicOperation(
            OperationShape opShape,
            ShapeId serviceId
    ) {
        var model = BenchmarkTestCases.model();
        ServiceShape service = model.expectShape(serviceId, ServiceShape.class);
        var converter = new SchemaConverter(model);
        return DynamicOperation.create(
                opShape,
                converter,
                model,
                service,
                TypeRegistry.empty(),
                (id, builder) -> {});
    }
}
