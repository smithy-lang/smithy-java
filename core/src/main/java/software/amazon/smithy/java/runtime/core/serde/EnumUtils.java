/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class EnumUtils {
    private EnumUtils() {
    }

    public static <K, V extends Enum<V>> Map<K, V> valueMapOf(Class<V> enumType, Function<? super V, K> indexFunction) {
        Map<K, V> result = new HashMap<>();
        for (var variant : EnumSet.allOf(enumType)) {
            var value = indexFunction.apply(variant);
            if (value != null) {
                result.put(value, variant);
            }
        }
        return result;
    }
}
