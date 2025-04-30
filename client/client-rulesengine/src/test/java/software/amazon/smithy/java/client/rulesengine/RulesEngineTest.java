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

public class RulesEngineTest {
    @Test
    public void canProvideFunctionsWhenLoadingRules() {
        var helloReturnValue = "hi!";
        var engine = new RulesEngine();
        var constantPool = new Object[] {helloReturnValue};
        var registers = new RegisterDefinition[0];
        var functions = List.of("hello");

        engine.addExtension(new RulesExtension() {
            @Override
            public List<RulesFunction> getFunctions() {
                return List.of(
                        new RulesFunction() {
                            @Override
                            public int getOperandCount() {
                                return 1;
                            }

                            @Override
                            public String getFunctionName() {
                                return "hello";
                            }

                            @Override
                            public Object apply1(Object value) {
                                return value;
                            }
                        });
            }
        });

        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.FN,
                0,
                RulesProgram.RETURN_ERROR
        };

        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, functions);
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString(helloReturnValue));
    }

    @Test
    public void failsEarlyWhenFunctionIsMissing() {
        var helloReturnValue = "hi!";
        var engine = new RulesEngine();
        var constantPool = new Object[] {helloReturnValue};
        var registers = new RegisterDefinition[0];
        var functions = List.of("hello");
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.FN,
                0,
                RulesProgram.RETURN_ERROR
        };

        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, functions));
    }

    @Test
    public void failsEarlyWhenTooManyRegisters() {
        var engine = new RulesEngine();
        var constantPool = new Object[0];
        List<String> functions = List.of();
        var bytecode = new byte[] {RulesProgram.VERSION, RulesProgram.RETURN_ERROR};
        var registers = new RegisterDefinition[257];
        for (var i = 0; i < 257; i++) {
            registers[i] = new RegisterDefinition("r" + i);
        }

        Assertions.assertThrows(IndexOutOfBoundsException.class,
                () -> engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, functions));
    }

    @Test
    public void callsCustomBuiltins() {
        var helloReturnValue = "hi!";
        var engine = new RulesEngine();
        var constantPool = new Object[] {helloReturnValue};
        var registers = new RegisterDefinition[2];
        registers[0] = new RegisterDefinition("foo", false, null, "customTest");

        // This register will try to fill in a default from a builtin named "unknown", but one doesn't exist so it
        // is initialized to null. It's not required, so this is allowed to be null. It's like if a built-in is unable
        // to optionally find your AWS::Auth::AccountId ID.
        registers[1] = new RegisterDefinition("bar", false, null, "unknown");

        // Add a built-in provider that just gets ignored.
        engine.addBuiltinProvider((name, ctx) -> null);

        engine.addBuiltinProvider((name, ctx) -> {
            if (name.equals("customTest")) {
                return helloReturnValue;
            }
            return null;
        });

        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.RETURN_ERROR
        };

        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString(helloReturnValue));
    }
}
