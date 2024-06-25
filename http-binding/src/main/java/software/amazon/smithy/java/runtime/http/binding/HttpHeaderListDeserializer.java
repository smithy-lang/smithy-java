/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.List;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeDeserializer;

final class HttpHeaderListDeserializer extends SpecificShapeDeserializer {

    private final List<String> values;

    HttpHeaderListDeserializer(List<String> values) {
        this.values = values;
    }

    @Override
    protected RuntimeException throwForInvalidState(Schema schema) {
        throw new UnsupportedOperationException("List header deserialization not supported for " + schema);
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        for (String value : values) {
            listMemberConsumer.accept(state, new HttpHeaderDeserializer(value));
        }
    }

    @Override
    public boolean isNull() {
        return values == null;
    }
}
