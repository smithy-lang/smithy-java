/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.TimestampFormatter;

final class HttpQueryStringDeserializer extends BasicStringValueDeserializer {

    private final List<String> values;

    HttpQueryStringDeserializer(List<String> values) {
        super(values.get(0), "HTTP query-string bindings");
        this.values = values;
    }

    @Override
    TimestampFormatter defaultTimestampFormatter() {
        return TimestampFormatter.Prelude.DATE_TIME;
    }

    @Override
    public <T extends List<?>> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        for (String value : values) {
            consumer.accept(state, new HttpQueryStringDeserializer(List.of(value)));
        }
    }

    @Override
    public int containerSize() {
        return values.size();
    }
}
