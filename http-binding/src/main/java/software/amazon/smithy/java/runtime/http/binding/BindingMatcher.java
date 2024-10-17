/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
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

    private static final TraitKey<HttpHeaderTrait> HTTP_HEADER = TraitKey.get(HttpHeaderTrait.class);
    private static final TraitKey<HttpLabelTrait> HTTP_LABEL = TraitKey.get(HttpLabelTrait.class);
    private static final TraitKey<HttpQueryTrait> HTTP_QUERY = TraitKey.get(HttpQueryTrait.class);
    private static final TraitKey<HttpQueryParamsTrait> HTTP_QUERY_PARAMS = TraitKey.get(HttpQueryParamsTrait.class);
    private static final TraitKey<HttpPrefixHeadersTrait> HTTP_PREFIX_HEADERS = TraitKey.get(
        HttpPrefixHeadersTrait.class
    );
    private static final TraitKey<HttpPayloadTrait> HTTP_PAYLOAD = TraitKey.get(HttpPayloadTrait.class);
    private static final TraitKey<HttpResponseCodeTrait> HTTP_RESPONSE_CODE = TraitKey.get(HttpResponseCodeTrait.class);

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
            if (member.hasTrait(HTTP_LABEL)) {
                return Binding.LABEL;
            }

            if (member.hasTrait(HTTP_QUERY)) {
                return Binding.QUERY;
            }

            if (member.hasTrait(HTTP_QUERY_PARAMS)) {
                return Binding.QUERY_PARAMS;
            }

            if (member.hasTrait(HTTP_HEADER)) {
                return Binding.HEADER;
            }

            if (member.hasTrait(HTTP_PREFIX_HEADERS)) {
                return Binding.PREFIX_HEADERS;
            }

            if (member.hasTrait(HTTP_PAYLOAD)) {
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
            if (member.hasTrait(HTTP_RESPONSE_CODE)) {
                return Binding.STATUS;
            }

            if (member.hasTrait(HTTP_HEADER)) {
                return Binding.HEADER;
            }

            if (member.hasTrait(HTTP_PREFIX_HEADERS)) {
                return Binding.PREFIX_HEADERS;
            }

            if (member.hasTrait(HTTP_PAYLOAD)) {
                return Binding.PAYLOAD;
            }

            return Binding.BODY;
        }
    }
}
