/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
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
    private HttpLabelTrait label;
    private HttpQueryTrait query;
    private HttpPayloadTrait payload;
    private HttpResponseCodeTrait responseCode;
    private HttpPrefixHeadersTrait prefixHeaders;
    private HttpQueryParamsTrait queryParams;

    private BindingMatcher(boolean isRequest) {
        this.isRequest = isRequest;
    }

    static BindingMatcher requestMatcher() {
        return new BindingMatcher(true);
    }

    static BindingMatcher responseMatcher() {
        return new BindingMatcher(false);
    }

    Binding match(SdkSchema member) {
        if (isRequest) {
            label = member.getTrait(HttpLabelTrait.class).orElse(null);
            if (label != null) {
                return Binding.LABEL;
            }
            query = member.getTrait(HttpQueryTrait.class).orElse(null);
            if (query != null) {
                return Binding.QUERY;
            }
            queryParams = member.getTrait(HttpQueryParamsTrait.class).orElse(null);
            if (queryParams != null) {
                return Binding.QUERY_PARAMS;
            }
        } else {
            responseCode = member.getTrait(HttpResponseCodeTrait.class).orElse(null);
            if (responseCode != null) {
                return Binding.STATUS;
            }
        }

        header = member.getTrait(HttpHeaderTrait.class).orElse(null);
        if (header != null) {
            return Binding.HEADER;
        }

        prefixHeaders = member.getTrait(HttpPrefixHeadersTrait.class).orElse(null);
        if (prefixHeaders != null) {
            return Binding.PREFIX_HEADERS;
        }

        payload = member.getTrait(HttpPayloadTrait.class).orElse(null);
        if (payload != null) {
            return Binding.PAYLOAD;
        }

        return Binding.BODY;
    }

    String header() {
        return Objects.requireNonNull(header, "Not a header binding").getValue();
    }

    String prefixHeaders() {
        return Objects.requireNonNull(prefixHeaders, "Not a prefix headers binding")
                .getValue();
    }
}
