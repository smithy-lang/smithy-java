/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.events.aws;

import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeDeserializer;

public final class AwsEventDeserializer extends SpecificShapeDeserializer {

    private final Schema expectedMember;
    private final ShapeDeserializer memberDeserializer;

    public AwsEventDeserializer(Schema expectedMember, ShapeDeserializer memberDeserializer) {
        this.expectedMember = expectedMember;
        this.memberDeserializer = memberDeserializer;
    }

    @Override
    protected RuntimeException throwForInvalidState(Schema schema) {
        throw new IllegalStateException(
            "Expected to parse a structure for Json-encoded event stream data,"
                + " but found " + schema
        );
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        consumer.accept(state, expectedMember, memberDeserializer);
    }
}
