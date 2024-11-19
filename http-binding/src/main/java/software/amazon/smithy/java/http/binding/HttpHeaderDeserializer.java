/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.core.serde.TimestampFormatter;

final class HttpHeaderDeserializer extends BasicStringValueDeserializer {

    HttpHeaderDeserializer(String value) {
        super(value, "HTTP header bindings");
    }

    @Override
    TimestampFormatter defaultTimestampFormatter() {
        return TimestampFormatter.Prelude.HTTP_DATE;
    }
}
