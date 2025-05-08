/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;

public class RulesVmTest {
    @Test
    public void throwsWhenUnableToResolveEndpoint() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[0];
        var bytecode = new byte[] {RulesProgram.VERSION};
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("No value returned from rules engine"));
    }

    @Test
    public void throwsForInvalidOpcode() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[0];
        var bytecode = new byte[] {RulesProgram.VERSION, 120};
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Unknown rules engine instruction: 120"));
    }

    @Test
    public void throwsWithContextWhenTypeIsInvalid() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[0];

        var bytecode = new byte[] {
                RulesProgram.VERSION,
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
        assertThat(e.getMessage(), containsString("at address 3"));
    }

    @Test
    public void throwsWithContextWhenBytecodeIsMalformed() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[0];

        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.RESOLVE_TEMPLATE // missing following byte
        };

        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Malformed bytecode encountered while evaluating rules engine"));
    }

    @Test
    public void failsIfRequiredRegisterMissing() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[1];
        var bytecode = new byte[] {RulesProgram.VERSION};
        registers[0] = new RegisterDefinition("foo", true, null, null);
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Required rules engine parameter missing: foo"));
    }

    @Test
    public void setsDefaultRegisterValues() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[1];
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.RETURN_ENDPOINT,
                0
        };
        registers[0] = new RegisterDefinition("foo", true, "https://foo.com", null);
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://foo.com"));
    }

    @Test
    public void resizesTheStackWhenNeeded() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {1};
        var registers = new RegisterDefinition[1];
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.RETURN_ENDPOINT,
                0
        };
        registers[0] = new RegisterDefinition("foo", false, null, null);
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var endpoint = program.resolveEndpoint(Context.create(), Map.of("foo", "https://foo.com"));

        assertThat(endpoint.toString(), containsString("https://foo.com"));
    }

    @Test
    public void resolvesTemplates() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {StringTemplate.from(Template.fromString("https://{foo}.bar"))};
        var registers = new RegisterDefinition[1];
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER, // 1 byte register
                0,
                RulesProgram.RESOLVE_TEMPLATE, // 2 byte constant
                0,
                0,
                RulesProgram.RETURN_ENDPOINT, // 1 byte, no headers or properties
                0
        };
        registers[0] = new RegisterDefinition("foo", false, "hi", null);
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://hi.bar"));
    }

    @Test
    public void resolvesNoExpressionTemplates() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {StringTemplate.from(Template.fromString("https://hi.bar"))};
        var registers = new RegisterDefinition[0];
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.RESOLVE_TEMPLATE, // 2 byte constant
                0,
                0,
                RulesProgram.RETURN_ENDPOINT, // 1 byte, no headers or properties
                0
        };
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://hi.bar"));
    }

    @Test
    public void wrapsInvalidURIs() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {StringTemplate.from(Template.fromString("!??!!\\"))};
        var registers = new RegisterDefinition[0];
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.RESOLVE_TEMPLATE, // 2 byte constant
                0,
                0,
                RulesProgram.RETURN_ENDPOINT, // 1 byte, no headers or properties
                0
        };
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Error creating URI"));
    }

    @Test
    public void createsMapForEndpointHeaders() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {StringTemplate.from(Template.fromString("https://hi.bar")), "abc", "def"};
        var registers = new RegisterDefinition[0];
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_CONST, // push map key "abc"
                1,
                RulesProgram.LOAD_CONST, // push list value 0, "def"
                2,
                RulesProgram.CREATE_LIST, // push list with one value, ["def"].
                1,
                RulesProgram.CREATE_MAP, // push with one KVP: {"abc": ["def"]} (the endpoint headers)
                1,
                RulesProgram.RESOLVE_TEMPLATE, // push resolved string template at constant 0 (2 byte constant)
                0,
                0,
                RulesProgram.RETURN_ENDPOINT, // Return an endpoint that does have headers.
                1
        };
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://hi.bar"));
        assertThat(endpoint.property(Endpoint.HEADERS), equalTo(Map.of("abc", List.of("def"))));
    }

    @Test
    public void testsIfRegisterSet() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {};
        var registers = new RegisterDefinition[] {new RegisterDefinition("hi", false, "abc", null)};
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.TEST_REGISTER_ISSET,
                0,
                RulesProgram.RETURN_VALUE
        };
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(true));
    }

    @Test
    public void testsIfValueRegisterSet() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {};
        var registers = new RegisterDefinition[] {new RegisterDefinition("hi", false, "abc", null)};
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.ISSET,
                RulesProgram.RETURN_VALUE
        };
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(true));
    }

    @Test
    public void testNotOpcode() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {};
        var registers = new RegisterDefinition[] {new RegisterDefinition("hi", false, false, null)};
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.NOT,
                RulesProgram.RETURN_VALUE
        };
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(true));
    }

    @Test
    public void testTrueOpcodes() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {};
        var registers = new RegisterDefinition[] {
                new RegisterDefinition("a", false, false, null),
                new RegisterDefinition("b", false, true, null),
                new RegisterDefinition("c", false, "foo", null),
        };
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.IS_TRUE,
                RulesProgram.LOAD_REGISTER,
                1,
                RulesProgram.IS_TRUE,
                RulesProgram.LOAD_REGISTER,
                2,
                RulesProgram.IS_TRUE,
                RulesProgram.TEST_REGISTER_IS_TRUE,
                0,
                RulesProgram.TEST_REGISTER_IS_TRUE,
                1,
                RulesProgram.TEST_REGISTER_IS_TRUE,
                2,
                RulesProgram.CREATE_LIST,
                6,
                RulesProgram.RETURN_VALUE};
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(List.of(false, true, false, false, true, false)));
    }

    @Test
    public void callsFunctions() {
        var engine = new RulesEngine();
        engine.addFunction(new RulesFunction() {
            @Override
            public int getOperandCount() {
                return 0;
            }

            @Override
            public String getFunctionName() {
                return "gimme";
            }

            @Override
            public Object apply0() {
                return "gimme";
            }
        });

        var constantPool = new Object[] {3, 8, false};
        var registers = new RegisterDefinition[] {new RegisterDefinition("a", false, "hi there", null)};
        var functions = List.of("stringEquals", "uriEncode", "substring", "gimme");
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.FN,
                0, // "hi there" == "hi there" : true
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.FN,
                1, // uriEncode "hi there" : "hi%20there"
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.LOAD_CONST,
                1,
                RulesProgram.LOAD_CONST,
                2,
                RulesProgram.FN,
                2, // "hi_there" -> "there"
                RulesProgram.FN,
                3, // call gimme()
                RulesProgram.CREATE_LIST,
                4, // ["gimme", "there", "hi%20there", true]
                RulesProgram.RETURN_VALUE};
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, functions);
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(List.of(true, "hi%20there", "there", "gimme")));
    }

    @Test
    public void appliesGetAttrOpcode() {
        var engine = new RulesEngine();
        var constantPool = new Object[] {AttrExpression.parse("foo")};
        var registers = new RegisterDefinition[] {new RegisterDefinition("a", false, null, null)};
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.GET_ATTR,
                0,
                0,
                RulesProgram.RETURN_VALUE};
        var program = engine.fromPrecompiled(ByteBuffer.wrap(bytecode), constantPool, registers, List.of());
        var result = program.run(Context.create(), Map.of("a", Map.of("foo", "hi")));

        assertThat(result, equalTo("hi"));
    }
}
