/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Loads the Smithy benchmark model from the classpath and exposes the runtime
 * objects needed by the JMH benchmark classes.
 *
 * <p>The model files are bundled as resources (see {@code build.gradle.kts}'s
 * {@code generateSmithyManifest} task) and discovered via
 * {@link Model#assembler()}'s default discovery mechanism.
 *
 * <p>Each benchmark is parameterized by a test case ID, which is the {@code id}
 * field of an entry in an operation's {@code @httpRequestTests} or
 * {@code @httpResponseTests} trait that is tagged {@code "serde-benchmark"}.
 * See {@link BenchmarkTestCases} for the test-case loading logic.
 */
final class BenchmarkContext {

    /** Smithy model loaded once per JVM. */
    static final Model MODEL = loadModel();

    /** Indexed by operation simple name, e.g. {@code "GetItem"} -> {@code OperationShape}. */
    private static final Map<String, OperationShape> OPERATIONS = indexOperations(MODEL);

    private BenchmarkContext() {}

    /**
     * Find the operation shape with the given simple name. Operation names are
     * unique across this benchmark model.
     */
    static OperationShape operation(String simpleName) {
        OperationShape shape = OPERATIONS.get(simpleName);
        if (shape == null) {
            throw new IllegalArgumentException("Unknown operation: " + simpleName);
        }
        return shape;
    }

    private static Model loadModel() {
        return Model.assembler(BenchmarkContext.class.getClassLoader())
                .discoverModels(BenchmarkContext.class.getClassLoader())
                .assemble()
                .unwrap();
    }

    private static Map<String, OperationShape> indexOperations(Model model) {
        Map<String, OperationShape> ops = new HashMap<>();
        for (OperationShape op : model.getOperationShapes()) {
            ops.put(op.getId().getName(), op);
        }
        return ops;
    }
}
