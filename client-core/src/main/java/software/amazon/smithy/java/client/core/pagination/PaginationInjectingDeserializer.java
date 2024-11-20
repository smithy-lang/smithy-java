/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentDeserializer;

/**
 * Replaces values of a top-level structure members with values for pagination.
 */
final class PaginationInjectingDeserializer extends SpecificShapeDeserializer {
    private final Document value;
    private final String tokenMember;
    private final Document tokenValue;
    private final String maxResultsMember;
    private final Document maxResultsValue;

    PaginationInjectingDeserializer(
        Document value,
        String tokenMember,
        String token,
        String pageSizeMember,
        int pageSize
    ) {
        this.value = value;
        this.tokenMember = tokenMember;
        this.tokenValue = token != null ? Document.createString(token) : null;
        this.maxResultsMember = pageSizeMember;
        this.maxResultsValue = Document.createInteger(pageSize);
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        for (var memberSchema : schema.members()) {
            // Replace member value with token if applicable.
            if (tokenValue != null && memberSchema.memberName().equals(tokenMember)) {
                consumer.accept(state, memberSchema, new DocumentDeserializer(tokenValue));
                continue;
            }

            // Replace member value with max results value if applicable.
            if (memberSchema.memberName().equals(maxResultsMember)) {
                consumer.accept(state, memberSchema, new DocumentDeserializer(maxResultsValue));
                continue;
            }

            // Otherwise just deserialize normally.
            var memberValue = value.getMember(memberSchema.memberName());
            if (memberValue != null) {
                consumer.accept(state, memberSchema, new DocumentDeserializer(memberValue));
            }
        }
    }
}
