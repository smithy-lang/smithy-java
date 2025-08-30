/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events.model;

import java.util.List;
import java.util.function.Supplier;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class TestOperation
        implements InputEventStreamingApiOperation<TestOperationInput, TestOperationOutput, TestEventStream>,
        OutputEventStreamingApiOperation<TestOperationInput, TestOperationOutput, TestEventStream> {

    private static final TestOperation $INSTANCE = new TestOperation();

    static final Schema $SCHEMA = Schema.createOperation(ShapeId.from("smithy.test.eventstreaming#TestOperation"));

    public static final ShapeId $ID = $SCHEMA.id();

    private static final TypeRegistry TYPE_REGISTRY = TypeRegistry.empty();

    private static final List<ShapeId> SCHEMES = List.of(ShapeId.from("smithy.api#noAuth"));

    private static final Schema INPUT_STREAM_MEMBER = TestOperationInput.$SCHEMA.member("stream");
    private static final Schema OUTPUT_STREAM_MEMBER = TestOperationOutput.$SCHEMA.member("outputStream");

    /**
     * Get an instance of this {@code ApiOperation}.
     *
     * @return An instance of this class.
     */
    public static TestOperation instance() {
        return $INSTANCE;
    }

    private TestOperation() {}

    @Override
    public ShapeBuilder<TestOperationInput> inputBuilder() {
        return TestOperationInput.builder();
    }

    @Override
    public Supplier<ShapeBuilder<TestEventStream>> inputEventBuilderSupplier() {
        return () -> TestEventStream.builder();
    }

    @Override
    public ShapeBuilder<TestOperationOutput> outputBuilder() {
        return TestOperationOutput.builder();
    }

    @Override
    public Supplier<ShapeBuilder<TestEventStream>> outputEventBuilderSupplier() {
        return () -> TestEventStream.builder();
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return TestOperationInput.$SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return TestOperationOutput.$SCHEMA;
    }

    @Override
    public TypeRegistry errorRegistry() {
        return TYPE_REGISTRY;
    }

    @Override
    public List<ShapeId> effectiveAuthSchemes() {
        return SCHEMES;
    }

    @Override
    public Schema inputStreamMember() {
        return INPUT_STREAM_MEMBER;
    }

    @Override
    public Schema outputStreamMember() {
        return OUTPUT_STREAM_MEMBER;
    }

    @Override
    public Schema idempotencyTokenMember() {
        return null;
    }

    @Override
    public ApiService service() {
        return EventStreamingTestServiceApiService.instance();
    }
}
