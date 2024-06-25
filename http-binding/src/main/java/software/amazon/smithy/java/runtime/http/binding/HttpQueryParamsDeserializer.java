/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeDeserializer;

final class HttpQueryParamsDeserializer extends SpecificShapeDeserializer {

    private final Map<String, List<String>> queryParams;

    public HttpQueryParamsDeserializer(Map<String, List<String>> queryParams) {
        this.queryParams = queryParams;
    }

    @Override
    protected RuntimeException throwForInvalidState(Schema schema) {
        throw new UnsupportedOperationException(schema + " is not a valid @httpQueryParams target");
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        for (Map.Entry<String, List<String>> e : queryParams.entrySet()) {
            consumer.accept(state, e.getKey(), new HttpQueryStringDeserializer(e.getValue()));
        }
    }
}
