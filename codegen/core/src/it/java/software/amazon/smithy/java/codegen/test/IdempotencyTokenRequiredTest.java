/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.IdempotencyTokenRequiredInput;

public class IdempotencyTokenRequiredTest {
    @Test
    void makesRequiredTokensClientOptional() {
        // Will not fail to build because the token was made client optional.
        IdempotencyTokenRequiredInput.builder().build();
    }
}
