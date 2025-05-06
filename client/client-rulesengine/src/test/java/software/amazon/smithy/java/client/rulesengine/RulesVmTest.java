/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

public class RulesVmTest {
    @Test
    public void throwsWithContextWhenTypeIsInvalid() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[0];

        var bytecode = new byte[] {
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.RESOLVE_TEMPLATE,
                0, // Refers to invalid type. Expects string, given integer.
                0
        };

        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Unexpected value type"));
        assertThat(e.getMessage(), containsString("at address 2"));
    }

    @Test
    public void throwsWithContextWhenBytecodeIsMalformed() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[0];

        var bytecode = new byte[] {
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.RESOLVE_TEMPLATE // missing following byte
        };

        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Malformed bytecode encountered while evaluating rules engine"));
    }
}
