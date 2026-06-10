/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.stream.Stream;

/**
 * Helpers for enumerating which {@link TestMode}s a client protocol test should run in.
 */
final class TestModes {

    private TestModes() {}

    /**
     * The modes available for an operation: always {@link TestMode#CODEGEN}, plus {@link TestMode#DYNAMIC} when a
     * document-backed model was built for the operation.
     */
    static Stream<TestMode> available(HttpTestOperation operation) {
        return operation.dynamicOperationModel() == null
                ? Stream.of(TestMode.CODEGEN)
                : Stream.of(TestMode.CODEGEN, TestMode.DYNAMIC);
    }
}
