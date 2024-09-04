/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface HttpHeaders {

    String getFirstHeader(String name);

    List<String> getHeader(String name);

    int size();

    default boolean isEmpty() {
        return size() == 0;
    }

    default Iterable<Map.Entry<String, List<String>>> iterator() {
        return toMap().entrySet();
    }

    default Map<String, List<String>> toMap() {
        Map<String, List<String>> result = new HashMap<>(size());
        for (var entry : iterator()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

}
