/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.HashMap;
import java.util.Map;

final class MapStorageContext implements Context {

    private final Map<Key<?>, Object> attributes = new HashMap<>();

    @Override
    public <T> void put(Key<T> key, T value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        return (T) attributes.get(key);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void copyTo(Context target) {
        for (var entry : attributes.entrySet()) {
            var key = (Key) entry.getKey();
            target.put(key, key.copyValue(entry.getValue()));
        }
    }
}
