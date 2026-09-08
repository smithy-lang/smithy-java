/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.Schema;

final class HttpBindingCodegen {

    static final String ID = "http-binding";

    private static final Object SETTINGS = new Object();

    private static final RuntimeCodecRegistry<HttpBindingWriter> REQUEST =
            new RuntimeCodecRegistry<>(new HttpBindingRuntimeCodegenBackend(false));
    private static final RuntimeCodecRegistry<HttpBindingWriter> RESPONSE =
            new RuntimeCodecRegistry<>(new HttpBindingRuntimeCodegenBackend(true));

    static final HttpBindingWriter NO_BINDING_WRITER = (struct, sink) -> {
        throw new UnsupportedOperationException("No generated HTTP binding writer");
    };

    private HttpBindingCodegen() {}

    static HttpBindingWriter writerOrSentinel(Schema schema, boolean isResponse, boolean strict) {
        if (!RuntimeCodegenFeature.available()) {
            if (strict) {
                throw new IllegalStateException(
                        "Runtime HTTP binding generation requires Java 25 or later; strict mode forbids fallback");
            }
            return NO_BINDING_WRITER;
        }
        HttpBindingWriter writer = (isResponse ? RESPONSE : REQUEST)
                .get(schema, SETTINGS, Selector.of(isResponse), strict);
        return writer == null ? NO_BINDING_WRITER : writer;
    }

    enum Selector implements RuntimeCodecBackend.MemberSelector {
        REQUEST(false),
        RESPONSE(true);

        private final boolean isResponse;

        Selector(boolean isResponse) {
            this.isResponse = isResponse;
        }

        static Selector of(boolean isResponse) {
            return isResponse ? RESPONSE : REQUEST;
        }

        @Override
        public boolean select(Schema root, Schema member) {
            return isSupported(bindings(root, isResponse)[member.memberIndex()]);
        }
    }

    static HttpBindingSchemaExtensions.Binding[] bindings(Schema struct, boolean isResponse) {
        var structBindings = HttpBindingSchemaExtensions.structBindingsOf(struct);
        return isResponse ? structBindings.response().bindings : structBindings.request().bindings;
    }

    static HttpBindingSchemaExtensions.MemberBinding[] memberBindings(Schema struct, boolean isResponse) {
        var structBindings = HttpBindingSchemaExtensions.structBindingsOf(struct);
        return isResponse
                ? structBindings.response().memberBindings
                : structBindings.request().memberBindings;
    }

    static boolean isSupported(HttpBindingSchemaExtensions.Binding binding) {
        return switch (binding) {
            case HEADER, QUERY, PREFIX_HEADERS, QUERY_PARAMS, PAYLOAD, STATUS -> true;
            default -> false;
        };
    }
}
