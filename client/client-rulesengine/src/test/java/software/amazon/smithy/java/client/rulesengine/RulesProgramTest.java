/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

public class RulesProgramTest {
    @Test
    public void failsWhenMissingVersion() {
        var engine = new RulesEngine();
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> engine.precompiledBuilder()
                        .bytecode(RulesProgram.RETURN_ERROR, (byte) 0, (byte) 0)
                        .build());
    }

    @Test
    public void failsWhenVersionIsTooBig() {
        var engine = new RulesEngine();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> engine.precompiledBuilder()
                        .bytecode((byte) -127, (byte) 0, (byte) 0)
                        .build());
    }

    @Test
    public void failsWhenNotEnoughBytes() {
        var engine = new RulesEngine();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> engine.precompiledBuilder().bytecode((byte) -1).build());
    }

    @Test
    public void failsWhenMissingParams() {
        var engine = new RulesEngine();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> engine.precompiledBuilder()
                        .bytecode(RulesProgram.VERSION, (byte) 1, (byte) 0)
                        .build());
    }

    @Test
    public void convertsProgramsToStrings() {
        var program = getErrorProgram();
        var str = program.toString();

        assertThat(str, containsString("Constants:"));
        assertThat(str, containsString("Registers:"));
        assertThat(str, containsString("Instructions:"));
        assertThat(str, containsString("0: String: Error!"));
        assertThat(str, containsString("0: ParamDefinition[name=a"));
        assertThat(str, containsString("003: LOAD_CONST"));
        assertThat(str, containsString("005: RETURN_ERROR"));
    }

    private RulesProgram getErrorProgram() {
        var engine = new RulesEngine();

        return engine.precompiledBuilder()
                .bytecode(
                        RulesProgram.VERSION,
                        (byte) 1, // params
                        (byte) 0, // registers
                        RulesProgram.LOAD_CONST,
                        (byte) 0,
                        RulesProgram.RETURN_ERROR)
                .constantPool("Error!")
                .parameters(new ParamDefinition("a"))
                .build();
    }

    @Test
    public void exposesConstantsAndRegisters() {
        var program = getErrorProgram();

        assertThat(program.getConstantPool().length, is(1));
        assertThat(program.getParamDefinitions().size(), is(1));
    }

    @Test
    public void runsPrograms() {
        var program = getErrorProgram();

        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of("a", "foo")));

        assertThat(e.getMessage(), containsString("Error!"));
    }
}
