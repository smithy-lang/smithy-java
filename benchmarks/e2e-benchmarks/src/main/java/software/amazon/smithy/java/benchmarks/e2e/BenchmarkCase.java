/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * One benchmark: a generated operation, the typed input to send on every iteration, and the canned response the
 * mock transport answers with.
 *
 * @param id                    the benchmark id, shared verbatim with the other SDKs
 * @param protocol              the protocol the id belongs to
 * @param operationName         the Smithy operation name, e.g. {@code GetItem}
 * @param source                whether the case came from a request test (request-side work dominates) or a
 *                              response test (deserialization dominates)
 * @param operation             the codegen-generated operation
 * @param input                 the input built once at setup and reused for every call
 * @param response              the canned response
 * @param outputPayloadMember   the output's blob {@code @httpPayload} member, if any, consumed fully after each
 *                              call (drained if streaming, otherwise its bytes are read) so the response payload
 *                              is actually touched, as ocs requires
 */
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
