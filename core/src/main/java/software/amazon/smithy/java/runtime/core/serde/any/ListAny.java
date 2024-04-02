/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class ListAny implements Any {

    private final SdkSchema schema;
    private final List<Any> value;

    ListAny(List<Any> value, SdkSchema schema) {
        // Validate the schema type.
        if (!(schema.type() == ShapeType.DOCUMENT || schema.type() == ShapeType.LIST)) {
            throw new SdkSerdeException("Expected list Any to have a list or document schema, but found " + schema);
        }

        // Ensure each element in the Any has the same schema and is a member.
        // If it's a member, ensure that each member is named 'member'.
        if (!value.isEmpty()) {
            var first = value.getFirst();
            if (first.schema().isMember() && !first.schema().memberName().equals("member")) {
                throw new SdkSerdeException(
                    "List Any member at position 0 has a member name of '" + first.schema().memberName() + "', "
                        + "but list members must be named 'member': " + first
                );
            } else if (!first.schema().isMember() && first.schema().type() != ShapeType.DOCUMENT) {
                throw new SdkSerdeException("Members of a list Any must be a member or a document, but found " + first);
            }
            for (int i = 1; i < value.size(); i++) {
                var current = value.get(i);
                if (!first.schema().equals(current.schema())) {
                    throw new SdkSerdeException(
                        "Every member of a list Any must use the same exact Schema. Expected element " + i
                            + " of the list to be " + first.schema() + ", but found " + current.schema()
                            + " in the list for " + schema
                    );
                }
            }
        }

        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema schema() {
        return schema;
    }

    @Override
    public ShapeType type() {
        return ShapeType.LIST;
    }

    @Override
    public List<Any> asList() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.beginList(schema, listElementSerializer -> {
            for (Any listElement : value) {
                listElement.serialize(listElementSerializer);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ListAny listAny = (ListAny) o;
        return Objects.equals(schema, listAny.schema) && Objects.equals(value, listAny.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, value);
    }

    @Override
    public String toString() {
        return "ListAny{value=" + value + ", schema=" + schema + '}';
    }
}
