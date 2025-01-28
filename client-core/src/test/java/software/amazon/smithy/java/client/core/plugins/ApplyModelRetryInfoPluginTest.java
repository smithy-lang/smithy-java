/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.IdempotentTrait;
import software.amazon.smithy.model.traits.ReadonlyTrait;
import software.amazon.smithy.model.traits.RetryableTrait;

public class ApplyModelRetryInfoPluginTest {
    @Test
    public void marksSafeWhenOperationIsReadOnly() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"), new ReadonlyTrait());
        var e = new CallException("err");

        ApplyModelRetryInfoPlugin.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
    }

    @Test
    public void marksSafeWhenOperationIsIdempotent() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"), new IdempotentTrait());
        var e = new CallException("err");

        ApplyModelRetryInfoPlugin.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
    }

    @Test
    public void marksSafeWhenErrorIsRetryable() {
        // No retry traits on the operation itself.
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));

        // The error is marked retryable.
        var errorSchema = Schema
                .structureBuilder(ShapeId.from("com#Err"), RetryableTrait.builder().throttling(true).build())
                .build();

        var e = new ModeledException(errorSchema, "err") {
            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void serialize(ShapeSerializer encoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException();
            }
        };

        ApplyModelRetryInfoPlugin.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
    }
}
