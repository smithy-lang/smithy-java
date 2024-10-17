/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;

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

    private BindingMatcher(Schema struct) {
        this.bindings = new Binding[struct.members().size()];
        for (var member : struct.members()) {
            bindings[member.memberIndex()] = doMatch(member);
        }
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

    protected abstract Binding doMatch(Schema member);

    static final class RequestMatcher extends BindingMatcher {
        RequestMatcher(Schema input) {
            super(input);
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
            super(output);
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
