/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;

abstract sealed class BindingMatcher {

    enum Binding {
        HEADER,
        QUERY,
        PAYLOAD,
        BODY,
        LABEL,
        STATUS,
        PREFIX_HEADERS,
        QUERY_PARAMS
    }

    private final Binding[] bindings;
    private final int responseStatus;
    private final boolean hasBody;
    private final boolean hasPayload;

    private BindingMatcher(Schema struct, int responseStatus) {
        this.responseStatus = responseStatus;
        boolean foundBody = false;
        boolean foundPayload = false;
        this.bindings = new Binding[struct.members().size()];
        for (var member : struct.members()) {
            var binding = doMatch(member);
            bindings[member.memberIndex()] = binding;
            foundBody = foundBody || binding == Binding.BODY;
            foundPayload = foundPayload || binding == Binding.PAYLOAD;
        }

        this.hasBody = foundBody;
        this.hasPayload = foundPayload;
    }

    static BindingMatcher requestMatcher(Schema input) {
        return new BindingMatcher.RequestMatcher(input);
    }

    static BindingMatcher responseMatcher(Schema output) {
        return new BindingMatcher.ResponseMatcher(output);
    }

    final Binding match(Schema member) {
        return bindings[member.memberIndex()];
    }

    final int responseStatus() {
        return responseStatus;
    }

    final boolean hasBody() {
        return hasBody;
    }

    final boolean hasPayload() {
        return hasPayload;
    }

    final boolean writeBody(boolean omitEmptyPayload) {
        return hasBody || (!omitEmptyPayload && !hasPayload);
    }

    protected abstract Binding doMatch(Schema member);

    static final class RequestMatcher extends BindingMatcher {
        RequestMatcher(Schema input) {
            super(input, -1);
        }

        protected Binding doMatch(Schema member) {
            if (member.hasTrait(TraitKey.HTTP_LABEL_TRAIT)) {
                return Binding.LABEL;
            }

            if (member.hasTrait(TraitKey.HTTP_QUERY_TRAIT)) {
                return Binding.QUERY;
            }

            if (member.hasTrait(TraitKey.HTTP_QUERY_PARAMS_TRAIT)) {
                return Binding.QUERY_PARAMS;
            }

            if (member.hasTrait(TraitKey.HTTP_HEADER_TRAIT)) {
                return Binding.HEADER;
            }

            if (member.hasTrait(TraitKey.HTTP_PREFIX_HEADERS_TRAIT)) {
                return Binding.PREFIX_HEADERS;
            }

            if (member.hasTrait(TraitKey.HTTP_PAYLOAD_TRAIT)) {
                return Binding.PAYLOAD;
            }

            return Binding.BODY;
        }
    }

    static final class ResponseMatcher extends BindingMatcher {
        ResponseMatcher(Schema output) {
            super(output, computeResponseStatus(output));
        }

        private static int computeResponseStatus(Schema struct) {
            if (struct.hasTrait(TraitKey.HTTP_ERROR_TRAIT)) {
                return struct.expectTrait(TraitKey.HTTP_ERROR_TRAIT).getCode();
            } else if (struct.hasTrait(TraitKey.ERROR_TRAIT)) {
                return struct.expectTrait(TraitKey.ERROR_TRAIT).getDefaultHttpStatusCode();
            } else {
                return -1;
            }
        }

        @Override
        protected Binding doMatch(Schema member) {
            if (member.hasTrait(TraitKey.HTTP_RESPONSE_CODE_TRAIT)) {
                return Binding.STATUS;
            }

            if (member.hasTrait(TraitKey.HTTP_HEADER_TRAIT)) {
                return Binding.HEADER;
            }

            if (member.hasTrait(TraitKey.HTTP_PREFIX_HEADERS_TRAIT)) {
                return Binding.PREFIX_HEADERS;
            }

            if (member.hasTrait(TraitKey.HTTP_PAYLOAD_TRAIT)) {
                return Binding.PAYLOAD;
            }

            return Binding.BODY;
        }
    }
}
