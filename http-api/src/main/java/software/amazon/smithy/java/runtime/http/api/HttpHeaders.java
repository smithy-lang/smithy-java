/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public interface HttpHeaders extends Iterable<Map.Entry<String, List<String>>> {

    String getFirstHeader(String name);

    List<String> getHeader(String name);

    int size();

    default boolean isEmpty() {
        return size() == 0;
    }

    @Override
    default Iterator<Map.Entry<String, List<String>>> iterator() {
        return Collections.unmodifiableMap(toMap()).entrySet().iterator();
    }

    default Map<String, List<String>> toMap() {
        Map<String, List<String>> result = new HashMap<>(size());
        for (var entry : this) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

}
