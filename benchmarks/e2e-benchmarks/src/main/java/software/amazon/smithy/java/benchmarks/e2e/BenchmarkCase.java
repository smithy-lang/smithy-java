/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;

record BenchmarkCase(
        String id,
        BenchmarkProtocol protocol,
        String operationName,
        Source source,
        ApiOperation<SerializableStruct, SerializableStruct> operation,
        SerializableStruct input,
        CannedResponse response,
        Schema outputPayloadMember,
        String fingerprint) {

    enum Source {
        REQUEST("request"),
        RESPONSE("response");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }
}
