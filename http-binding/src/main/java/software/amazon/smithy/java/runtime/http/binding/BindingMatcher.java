/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;

final class BindingMatcher {

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

    private final boolean isRequest;
    private HttpHeaderTrait header;
    private HttpPrefixHeadersTrait prefixHeaders;

    private BindingMatcher(boolean isRequest) {
        this.isRequest = isRequest;
    }

    static BindingMatcher requestMatcher() {
        return new BindingMatcher(true);
    }

    static BindingMatcher responseMatcher() {
        return new BindingMatcher(false);
    }

    Binding match(Schema member) {
        if (isRequest) {
            if (member.getTrait(HttpLabelTrait.class) != null) {
                return Binding.LABEL;
            }

            if (member.getTrait(HttpQueryTrait.class) != null) {
                return Binding.QUERY;
            }

            if (member.getTrait(HttpQueryParamsTrait.class) != null) {
                return Binding.QUERY_PARAMS;
            }
        } else {
            if (member.getTrait(HttpResponseCodeTrait.class) != null) {
                return Binding.STATUS;
            }
        }

        header = member.getTrait(HttpHeaderTrait.class);
        if (header != null) {
            return Binding.HEADER;
        }

        prefixHeaders = member.getTrait(HttpPrefixHeadersTrait.class);
        if (prefixHeaders != null) {
            return Binding.PREFIX_HEADERS;
        }

        if (member.getTrait(HttpPayloadTrait.class) != null) {
            return Binding.PAYLOAD;
        }

        return Binding.BODY;
    }

    String header() {
        return Objects.requireNonNull(header, "Not a header binding").getValue();
    }

    String prefixHeaders() {
        return Objects.requireNonNull(prefixHeaders, "Not a prefix headers binding").getValue();
    }
}
