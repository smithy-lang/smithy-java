/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.AllAuth;
import software.amazon.smithy.java.codegen.test.model.NoAuth;
import software.amazon.smithy.java.codegen.test.model.ScopedAuth;
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait;
import software.amazon.smithy.model.traits.HttpBasicAuthTrait;
import software.amazon.smithy.model.traits.synthetic.NoAuthTrait;

public class EffectiveAuthSchemeTest {
    @Test
    void generatedOperationHaveExpectedSchemes() {
        assertEquals(new NoAuth().effectiveAuthSchemes(), List.of(NoAuthTrait.ID));
        assertEquals(
            new AllAuth().effectiveAuthSchemes(),
            List.of(HttpApiKeyAuthTrait.ID, HttpBasicAuthTrait.ID)
        );
        assertEquals(new ScopedAuth().effectiveAuthSchemes(), List.of(HttpBasicAuthTrait.ID));
    }
}
