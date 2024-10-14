/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;

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
    }

    static BindingMatcher requestMatcher(Schema input) {
        return new BindingMatcher.RequestMatcher(input);
    }

    static BindingMatcher responseMatcher(Schema output) {
        return new BindingMatcher.ResponseMatcher(output);
    }

    final Binding match(Schema member) {
        var index = member.memberIndex();
        var result = bindings[index];
        if (result == null) {
            result = bindings[index] = doMatch(member);
        }
        return result;
    }

    protected abstract Binding doMatch(Schema member);

    static final class RequestMatcher extends BindingMatcher {
        RequestMatcher(Schema input) {
            super(input);
        }

        protected Binding doMatch(Schema member) {
            if (member.hasTrait(HttpLabelTrait.class)) {
                return Binding.LABEL;
            }

            if (member.hasTrait(HttpQueryTrait.class)) {
                return Binding.QUERY;
            }

            if (member.hasTrait(HttpQueryParamsTrait.class)) {
                return Binding.QUERY_PARAMS;
            }

            if (member.hasTrait(HttpHeaderTrait.class)) {
                return Binding.HEADER;
            }

            if (member.hasTrait(HttpPrefixHeadersTrait.class)) {
                return Binding.PREFIX_HEADERS;
            }

            if (member.hasTrait(HttpPayloadTrait.class)) {
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
            if (member.hasTrait(HttpResponseCodeTrait.class)) {
                return Binding.STATUS;
            }

            if (member.hasTrait(HttpHeaderTrait.class)) {
                return Binding.HEADER;
            }

            if (member.hasTrait(HttpPrefixHeadersTrait.class)) {
                return Binding.PREFIX_HEADERS;
            }

            if (member.hasTrait(HttpPayloadTrait.class)) {
                return Binding.PAYLOAD;
            }

            return Binding.BODY;
        }
    }
}
