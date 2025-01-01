/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.Locale;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait;

final class HttpPrefixHeadersDeserializer extends SpecificShapeDeserializer {

    private final HttpHeaders headers;

    public HttpPrefixHeadersDeserializer(HttpHeaders headers) {
        this.headers = headers;
    }

    @Override
    protected RuntimeException throwForInvalidState(Schema schema) {
        throw new SerializationException("httpPrefixHeaders must be bound to a string-valued map");
    }

    @Override
    public <T extends Map<?, ?>> void readStringMap(
            Schema schema,
            T state,
            MapMemberConsumer<String, T> consumer
    ) {
        HttpPrefixHeadersTrait trait = schema.expectTrait(TraitKey.HTTP_PREFIX_HEADERS_TRAIT);
        var prefix = trait.getValue().toLowerCase(Locale.ENGLISH);
        for (var entry : headers) {
            var name = entry.getKey();
            if (PrefixConstants.OMITTED_HEADER_NAMES.contains(name) || !name.startsWith(prefix)) {
                continue;
            }
            consumer.accept(state, name.substring(prefix.length()), new HeaderValueDeserializer(name));
        }
    }

    private class HeaderValueDeserializer extends SpecificShapeDeserializer {
        private final String headerName;

        public HeaderValueDeserializer(String headerName) {
            this.headerName = headerName;
        }

        @Override
        protected RuntimeException throwForInvalidState(Schema schema) {
            throw new SerializationException("Header values must be a string");
        }

        @Override
        public String readString(Schema schema) {
            var value = headers.firstValue(headerName);
            return value == null ? "" : value;
        }

        @Override
        public boolean isNull() {
            return false; // we return empty string if the header is not set
        }
    }
}
