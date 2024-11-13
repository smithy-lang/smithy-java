/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.exceptions;

import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SchemaUtils;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public final class InternalServerError extends ModeledApiException {

    private static final ShapeId ID = ShapeId.from(
        "software.amazon.smithy.exceptions#InternalServerError"
    );

    private static final Schema SCHEMA = Schema.structureBuilder(ID).build();
    private static final Schema SCHEMA_MESSAGE = SCHEMA.member("message");

    public InternalServerError(String message) {
        super(SCHEMA, message);
    }

    public InternalServerError(Throwable cause) {
        this("Internal Server Error", cause);
    }

    public InternalServerError(String message, Throwable cause) {
        super(SCHEMA, message, cause);
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
