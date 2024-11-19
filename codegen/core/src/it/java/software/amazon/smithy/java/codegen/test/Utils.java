/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import java.lang.reflect.InvocationTargetException;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;

final class Utils {
    private Utils() {}

    static <T extends SerializableStruct> T pojoToDocumentRoundTrip(T pojo) {
        var document = Document.createTyped(pojo);
        ShapeBuilder<T> builder = getBuilder(pojo);
        document.deserializeInto(builder);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    static <P extends SerializableShape> ShapeBuilder<P> getBuilder(P pojo) {
        try {
            var method = pojo.getClass().getDeclaredMethod("builder");
            return (ShapeBuilder<P>) method.invoke(pojo);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
