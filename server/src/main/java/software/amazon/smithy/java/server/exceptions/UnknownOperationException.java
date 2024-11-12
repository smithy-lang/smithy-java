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

public class UnknownOperationException extends ModeledApiException {

    public static final ShapeId ID = ShapeId.from(
        "software.amazon.smithy.exceptions#UnknownOperationException"
    );

    private static final Schema SCHEMA = Schema.structureBuilder(ID).build();

    public UnknownOperationException(String message) {
        super(SCHEMA, message);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {}

    @Override
    public Object getMemberValue(Schema member) {
        return SchemaUtils.validateMemberInSchema(SCHEMA, member, null);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
