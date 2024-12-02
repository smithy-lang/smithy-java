/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Replaces values of a top-level structure members with values for pagination.
 *
 * @param <I> Input shape type for paginated operation.
 */
final class PaginationInputSetter<I extends SerializableStruct> {
    private final I input;
    private final ApiOperation<I, ?> operation;
    private final Schema inputTokenSchema;
    private final Schema maxResultsSchema;

    PaginationInputSetter(
        I input,
        ApiOperation<I, ?> operation,
        String inputTokenMember,
        String maxResultsMember
    ) {
        this.input = input;
        this.operation = operation;
        this.inputTokenSchema = input.schema().member(inputTokenMember);
        this.maxResultsSchema = maxResultsMember != null ? input.schema().member(maxResultsMember) : null;
    }

    I create(String token, Integer maxResults) {
        var builder = operation.inputBuilder();
        SchemaUtils.copyShape(input, builder);
        if (token != null) {
            builder.setMemberValue(inputTokenSchema, token);
        }
        if (maxResults != null) {
            builder.setMemberValue(maxResultsSchema, maxResults);
        }
        return builder.build();
    }
}
