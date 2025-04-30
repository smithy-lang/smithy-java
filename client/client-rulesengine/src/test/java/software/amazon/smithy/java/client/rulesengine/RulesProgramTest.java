/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

public class RulesProgramTest {
    @Test
    public void failsWhenMissingVersion() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {"Error!"};
        var registers = new RegisterDefinition[] {new RegisterDefinition("a")};
        var bytecode = new byte[] {RulesProgram.RETURN_ERROR};

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of()));
    }

    @Test
    public void failsWhenVersionIsTooBig() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {"Error!"};
        var registers = new RegisterDefinition[] {new RegisterDefinition("a")};
        var bytecode = new byte[] {-127}; // assume we will never have this many versions.

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of()));
    }

    @Test
    public void convertsProgramsToStrings() {
        var program = getErrorProgram();
        var str = program.toString();

        assertThat(str, containsString("Constants:"));
        assertThat(str, containsString("Registers:"));
        assertThat(str, containsString("Instructions:"));
        assertThat(str, containsString("0: String: Error!"));
        assertThat(str, containsString("0: RegisterDefinition[name=a"));
        assertThat(str, containsString("001: LOAD_CONST"));
        assertThat(str, containsString("003: RETURN_ERROR"));
    }

    private RulesProgram getErrorProgram() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {"Error!"};
        var registers = new RegisterDefinition[] {new RegisterDefinition("a")};

        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.RETURN_ERROR
        };

        return engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
    }

    @Test
    public void exposesConstantsAndRegisters() {
        var program = getErrorProgram();

        assertThat(program.getConstantPool().length, is(1));
        assertThat(program.getRegisterDefinitions().length, is(1));
    }

    @Test
    public void runsPrograms() {
        var program = getErrorProgram();

        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of("a", "foo")));

        assertThat(e.getMessage(), containsString("Error!"));
    }
}
