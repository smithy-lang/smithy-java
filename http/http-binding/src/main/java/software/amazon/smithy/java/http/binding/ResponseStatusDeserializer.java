/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;

final class ResponseStatusDeserializer extends SpecificShapeDeserializer {
    private final int responseStatus;

    public ResponseStatusDeserializer(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    @Override
    public int readInteger(Schema schema) {
        return responseStatus;
    }
}
