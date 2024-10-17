/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.net.http.HttpHeaders;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeDeserializer;
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
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        HttpPrefixHeadersTrait trait = schema.expectTrait(TraitKey.HTTP_PREFIX_HEADERS_TRAIT);
        var prefix = trait.getValue();
        var headersMap = headers.map();
        for (String headerName : headersMap.keySet()) {
            if (PrefixConstants.OMITTED_HEADER_NAMES.contains(headerName)
                || !headerName.startsWith(prefix)) {
                continue;
            }
            consumer.accept(state, headerName.substring(prefix.length()), new HeaderValueDeserializer(headerName));
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
            return headers.firstValue(headerName).orElse("");
        }

        @Override
        public boolean isNull() {
            return false; // we return empty string if the header is not set
        }
    }
}
