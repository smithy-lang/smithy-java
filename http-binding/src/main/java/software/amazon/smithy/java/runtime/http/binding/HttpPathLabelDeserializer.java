/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;

final class HttpPathLabelDeserializer extends BasicStringValueDeserializer {

    HttpPathLabelDeserializer(String value) {
        super(value, "HTTP path bindings");
    }

    @Override
    TimestampFormatter defaultTimestampFormatter() {
        return TimestampFormatter.Prelude.DATE_TIME;
    }
}
