/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.EndpointContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;

public class RulesVmTest {
    @Test
    public void throwsWhenUnableToResolveEndpoint() {
        var engine = new RulesEngine();
        var program = engine.precompiledBuilder()
                .bytecode(RulesProgram.VERSION, (byte) 0, (byte) 0)
                .constantPool(1)
                .build();
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("No value returned from rules engine"));
    }

    @Test
    public void throwsForInvalidOpcode() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {RulesProgram.VERSION, (byte) 0, (byte) 0, 120};
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(1)
                .build();
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Unknown rules engine instruction: 120"));
    }

    @Test
    public void throwsWithContextWhenTypeIsInvalid() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 0, // params
                (byte) 0, // registers
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.RESOLVE_TEMPLATE,
                0, // Refers to invalid type. Expects string, given integer.
                0
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(1)
                .build();
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Unexpected value type"));
        assertThat(e.getMessage(), containsString("at address 5"));
    }

    @Test
    public void throwsWithContextWhenBytecodeIsMalformed() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 0, // params
                (byte) 0, // registers
                RulesProgram.LOAD_CONST,
                0,
                RulesProgram.RESOLVE_TEMPLATE // missing following byte
        };

        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(1)
                .build();
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Malformed bytecode encountered while evaluating rules engine"));
    }

    @Test
    public void failsIfRequiredRegisterMissing() {
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
        };
        var program = new RulesEngine().precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(1)
                .parameters(new ParamDefinition("foo", true, null, null))
                .build();
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Required rules engine parameter missing: foo"));
    }

    @Test
    public void setsDefaultRegisterValues() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.RETURN_ENDPOINT,
                0
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(1)
                .parameters(new ParamDefinition("foo", true, "https://foo.com", null))
                .build();
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://foo.com"));
    }

    @Test
    public void resizesTheStackWhenNeeded() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
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
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(1)
                .parameters(new ParamDefinition("foo", false, null, null))
                .build();
        var endpoint = program.resolveEndpoint(Context.create(), Map.of("foo", "https://foo.com"));

        assertThat(endpoint.toString(), containsString("https://foo.com"));
    }

    @Test
    public void resolvesTemplates() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
                RulesProgram.LOAD_REGISTER, // 1 byte register
                0,
                RulesProgram.RESOLVE_TEMPLATE, // 2 byte constant
                0,
                0,
                RulesProgram.RETURN_ENDPOINT, // 1 byte, no headers or properties
                0
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(StringTemplate.from(Template.fromString("https://{foo}.bar")))
                .parameters(new ParamDefinition("foo", false, "hi", null))
                .build();
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://hi.bar"));
    }

    @Test
    public void resolvesNoExpressionTemplates() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 0, // params
                (byte) 0, // registers
                RulesProgram.RESOLVE_TEMPLATE, // 2 byte constant
                0,
                0,
                RulesProgram.RETURN_ENDPOINT, // 1 byte, no headers or properties
                0
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(StringTemplate.from(Template.fromString("https://hi.bar")))
                .build();
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://hi.bar"));
    }

    @Test
    public void wrapsInvalidURIs() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 0, // params
                (byte) 0, // registers
                RulesProgram.RESOLVE_TEMPLATE, // 2 byte constant
                0,
                0,
                RulesProgram.RETURN_ENDPOINT, // 1 byte, no headers or properties
                0
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(StringTemplate.from(Template.fromString("!??!!\\")))
                .build();
        var e = Assertions.assertThrows(RulesEvaluationError.class,
                () -> program.resolveEndpoint(Context.create(), Map.of()));

        assertThat(e.getMessage(), containsString("Error creating URI"));
    }

    @Test
    public void createsMapForEndpointHeaders() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 0, // params
                (byte) 0, // registers
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
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(StringTemplate.from(Template.fromString("https://hi.bar")), "abc", "def")
                .build();
        var endpoint = program.resolveEndpoint(Context.create(), Map.of());

        assertThat(endpoint.toString(), containsString("https://hi.bar"));
        assertThat(endpoint.property(EndpointContext.HEADERS), equalTo(Map.of("abc", List.of("def"))));
    }

    @Test
    public void testsIfRegisterSet() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
                RulesProgram.TEST_REGISTER_ISSET,
                0,
                RulesProgram.RETURN_VALUE
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .parameters(new ParamDefinition("hi", false, "abc", null))
                .build();
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(true));
    }

    @Test
    public void testsIfValueRegisterSet() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.ISSET,
                RulesProgram.RETURN_VALUE
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .parameters(new ParamDefinition("hi", false, "abc", null))
                .build();

        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(true));
    }

    @Test
    public void testNotOpcode() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.NOT,
                RulesProgram.RETURN_VALUE
        };
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .parameters(new ParamDefinition("hi", false, false, null))
                .build();
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(true));
    }

    @Test
    public void testTrueOpcodes() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 3, // params
                (byte) 0, // registers
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
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .parameters(
                        new ParamDefinition("a", false, false, null),
                        new ParamDefinition("b", false, true, null),
                        new ParamDefinition("c", false, "foo", null))
                .build();
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

        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
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
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(3, 8, false)
                .parameters(new ParamDefinition("a", false, "hi there", null))
                .functionNames("stringEquals", "uriEncode", "substring", "gimme")
                .build();
        var result = program.run(Context.create(), Map.of());

        assertThat(result, equalTo(List.of(true, "hi%20there", "there", "gimme")));
    }

    @Test
    public void appliesGetAttrOpcode() {
        var engine = new RulesEngine();
        var bytecode = new byte[] {
                RulesProgram.VERSION,
                (byte) 1, // params
                (byte) 0, // registers
                RulesProgram.LOAD_REGISTER,
                0,
                RulesProgram.GET_ATTR,
                0,
                0,
                RulesProgram.RETURN_VALUE};
        var program = engine.precompiledBuilder()
                .bytecode(bytecode)
                .constantPool(AttrExpression.parse("foo"))
                .parameters(new ParamDefinition("a", false, null, null))
                .build();
        var result = program.run(Context.create(), Map.of("a", Map.of("foo", "hi")));

        assertThat(result, equalTo("hi"));
    }
}
