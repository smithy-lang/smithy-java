/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.jackson;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A cache of structure member field names to SerializableString.
 */
final class SerializedStringCache {

    private final ConcurrentMap<String, SerializableString> cache = new ConcurrentHashMap<>();

    SerializableString create(String fieldName) {
        var serializedFieldName = cache.get(fieldName);

        if (serializedFieldName != null) {
            return serializedFieldName;
        }

        SerializableString fresh = new SerializedString(fieldName);
        SerializableString existing = cache.putIfAbsent(fieldName, fresh);
        return existing != null ? existing : fresh;
    }
}
