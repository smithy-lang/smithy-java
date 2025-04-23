/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;

final class ServiceLoaderLoader {
    static <T> Map<String, T> load(Class<T> clazz, Function<T, String> id) {
        Map<String, T> s = new HashMap<>();
        for (T service : ServiceLoader.load(clazz)) {
            s.put(id.apply(service), service);
        }
        return s;
    }
}
