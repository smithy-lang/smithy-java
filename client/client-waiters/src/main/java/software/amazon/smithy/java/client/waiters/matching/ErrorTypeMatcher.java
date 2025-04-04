/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.matching;

import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

record ErrorTypeMatcher<I extends SerializableStruct, O extends SerializableStruct>(String errorName)
        implements Matcher<I, O> {
    @Override
    public boolean matches(SerializableStruct input, SerializableStruct output, ModeledException exception) {
        if (exception == null) {
            return false;
        }
        // If fully qualified ID is provided compare as shapeID
        if (errorName.indexOf('#') != -1) {
            return ShapeId.from(errorName).equals(exception.schema().id());
        }
        return exception.schema().id().getName().equals(errorName);
    }
}
