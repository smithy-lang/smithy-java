/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.Header;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;

final class ApacheHttpHeaders implements HttpHeaders {
    private final Header[] headers;
    private volatile Map<String, List<String>> materialized;

    ApacheHttpHeaders(Header[] headers) {
        this.headers = headers == null ? new Header[0] : headers.clone();
    }

    @Override
    public List<String> allValues(String name) {
        return allValuesCanonical(HeaderName.canonicalize(name));
    }

    @Override
    public List<String> allValues(HeaderName name) {
        return allValuesCanonical(name.name());
    }

    @Override
    public int size() {
        return headers.length;
    }

    @Override
    public Map<String, List<String>> map() {
        Map<String, List<String>> result = materialized;
        if (result != null) {
            return result;
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Header header : headers) {
            grouped.computeIfAbsent(HeaderName.canonicalize(header.getName()), ignored -> new ArrayList<>(1))
                    .add(header.getValue());
        }
        materialized = grouped;
        return grouped;
    }

    private List<String> allValuesCanonical(String canonical) {
        Map<String, List<String>> cached = materialized;
        if (cached != null) {
            return cached.getOrDefault(canonical, Collections.emptyList());
        }

        List<String> values = null;
        for (Header header : headers) {
            if (HeaderName.canonicalize(header.getName()).equals(canonical)) {
                if (values == null) {
                    values = new ArrayList<>(2);
                }
                values.add(header.getValue());
            }
        }
        return values == null ? Collections.emptyList() : values;
    }
}
