/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.List;
import java.util.Map;

public interface ModifiableHttpHeaders extends HttpHeaders {

    static ModifiableHttpHeaders create() {
        return new SimpleModifiableHttpHeaders();
    }

    void putHeader(String name, String value);

    void putHeader(Map<String, List<String>> headers);

    void putHeader(String name, List<String> values);

    void removeHeader(String name);
}
