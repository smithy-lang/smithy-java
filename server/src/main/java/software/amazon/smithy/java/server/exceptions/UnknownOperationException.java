/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.exceptions;

import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;

public final class UnknownOperationException extends ModeledApiException {

    static final ShapeId ID = ShapeId.from(
        "software.amazon.smithy.exceptions#UnknownOperationException"
    );

    private static final Schema SCHEMA = Schema.structureBuilder(
        ID,
        new ErrorTrait("client")
    )
        .build();

    private static final Schema SCHEMA_MESSAGE = SCHEMA.member("message");

    public UnknownOperationException() {
        super(SCHEMA, "Unknown Operation");
    }

    public UnknownOperationException(String message) {
        super(SCHEMA, message);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_MESSAGE, getMessage());
    }

    @Override
    public Object getMemberValue(Schema member) {
        return SchemaUtils.validateMemberInSchema(SCHEMA, member, null);
    }
}
