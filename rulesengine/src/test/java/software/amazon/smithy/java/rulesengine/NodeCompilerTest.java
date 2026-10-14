/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;

/**
 * Covers {@link NodeCompiler}, which is the only way a rules program is evaluated now that the bytecode
 * interpreter is gone.
 *
 * <p>The broad correctness gate is {@code EndpointRulesetConformanceTest}, which resolves every endpoint
 * test case every AWS ruleset declares. These cases pin down the shapes those rulesets do not happen to
 * contain: in particular the inputs where folding {@code substring} plus {@code stringEquals} into a
 * bounded {@code regionMatches} would <em>not</em> be equivalent, so the fold must be declined. Each of
 * those asserts the outcome the unfused form produces, which is the semantics the rules language defines.
 */
class NodeCompilerTest {

    // LOAD_REGISTER 0; SUBSTRING start end reverse; JNN_OR_POP -> past the ""; LOAD_CONST ""; LOAD_CONST x;
    // STRING_EQUALS; RETURN_VALUE -- the prefix/suffix test idiom the rules compiler emits.
    private static byte[] substringEqualsProgram(int start, int end, boolean reverse) {
        return new byte[] {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.SUBSTRING,
                (byte) start,
                (byte) end,
                (byte) (reverse ? 1 : 0),
                Opcodes.JNN_OR_POP,
                0,
                2,
                Opcodes.LOAD_CONST,
                0, // ""
                Opcodes.LOAD_CONST,
                1, // the expected literal
                Opcodes.STRING_EQUALS,
                Opcodes.RETURN_VALUE
        };
    }

    @Test
    void fusesSubstringEqualsWhenExactlyEquivalent() {
        var code = substringEqualsProgram(0, 6, true);
        var pool = new Object[] {"", "--x-s3"};
        Bytecode bytecode = condition(code, pool);

        assertInstanceOf(CompiledNode.SubstringEqReg.class,
                bytecode.conditionNode(0),
                "a 6-character ASCII literal over a 6-character range should fuse");

        assertTrue(evaluate(bytecode, "my-bucket--x-s3"));
        assertTrue(evaluate(bytecode, "--x-s3"));
        assertFalse(evaluate(bytecode, "plain-bucket"));
        assertFalse(evaluate(bytecode, "short"), "a range longer than the value matches nothing");
        assertFalse(evaluate(bytecode, ""));
        assertFalse(evaluate(bytecode, null));
    }

    @Test
    void declinesFusionWhenTheLiteralIsShorterThanTheRange() {
        // substringEquals compares only as many characters of the literal as the range covers, so fusing
        // would report a match for anything whose last six characters merely start with "--".
        var code = substringEqualsProgram(0, 6, true);
        var pool = new Object[] {"", "--"};
        Bytecode bytecode = condition(code, pool);

        assertFalse(bytecode.conditionNode(0) instanceof CompiledNode.SubstringEqReg);
        assertFalse(evaluate(bytecode, "bucket--x-s3"), "\"--x-s3\" is not equal to \"--\"");
        assertFalse(evaluate(bytecode, "ab--"));
    }

    @Test
    void declinesFusionWhenTheLiteralIsLongerThanTheRange() {
        // Last two characters of "xx--" are "--", which are also the first two of the literal, so a
        // bounded regionMatches would call this a match where stringEquals does not.
        var code = substringEqualsProgram(0, 2, true);
        var pool = new Object[] {"", "--x-s3"};
        Bytecode bytecode = condition(code, pool);

        assertFalse(bytecode.conditionNode(0) instanceof CompiledNode.SubstringEqReg);
        assertFalse(evaluate(bytecode, "xx--"), "\"--\" is not equal to \"--x-s3\"");
    }

    @Test
    void declinesFusionForNonAsciiLiterals() {
        // getSubstring rejects a range containing non-ASCII and so yields "" through the coalesce, while
        // substringEquals would match the characters.
        var code = substringEqualsProgram(0, 2, false);
        var pool = new Object[] {"", "éé"};
        Bytecode bytecode = condition(code, pool);

        assertFalse(bytecode.conditionNode(0) instanceof CompiledNode.SubstringEqReg);
        assertFalse(evaluate(bytecode, "éérest"), "a non-ASCII range reads as unmatched");
    }

    @Test
    void declinesFusionForAnEmptyLiteral() {
        // A zero-length substring coalesces to "", which equals the empty literal, whereas the fused
        // substringEquals operation reports false. The range length deliberately matches the expected
        // length so the non-empty guard is the only reason this cannot fuse.
        var code = substringEqualsProgram(0, 0, false);
        var pool = new Object[] {"", ""};
        Bytecode bytecode = condition(code, pool);

        assertFalse(bytecode.conditionNode(0) instanceof CompiledNode.SubstringEqReg);
        assertTrue(evaluate(bytecode, "anything"), "a zero-length substring coalesces to \"\"");
    }

    @Test
    void evaluatesDynamicAllowDotsForHostLabels() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LOAD_REGISTER,
                1,
                Opcodes.IS_VALID_HOST_LABEL,
                Opcodes.RETURN_VALUE
        };
        Bytecode bytecode = condition(code, new Object[0]);

        assertInstanceOf(CompiledNode.IsValidHostLabelNode.class, bytecode.conditionNode(0));

        var evaluator = new BytecodeEvaluator(
                bytecode,
                new RulesExtension[0],
                RegisterFiller.of(bytecode, Map.of()));
        evaluator.reset(Context.create(), params("example.com", Boolean.TRUE));
        assertTrue(evaluator.test(0));
        evaluator.reset(Context.create(), params("example.com", Boolean.FALSE));
        assertFalse(evaluator.test(0));
        evaluator.reset(Context.create(), params("example.com", "true"));
        assertThrows(RulesEvaluationError.class, () -> evaluator.test(0));
    }

    @Test
    void rejectsNonBooleanConstantAllowDotsForHostLabels() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LOAD_CONST,
                0,
                Opcodes.IS_VALID_HOST_LABEL,
                Opcodes.RETURN_VALUE
        };
        Bytecode bytecode = condition(code, new Object[] {"true"});

        var error = assertThrows(RulesEvaluationError.class, () -> bytecode.conditionNode(0));

        assertTrue(
                error.getMessage().contains(
                        "IS_VALID_HOST_LABEL allowDots must be a boolean, found java.lang.String"),
                error.getMessage());
    }

    @Test
    void compilesCoalesceChains() {
        // coalesce(reg0, reg1, "https://fallback.example.com") used as an endpoint URL.
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.JNN_OR_POP,
                0,
                7,
                Opcodes.LOAD_REGISTER,
                1,
                Opcodes.JNN_OR_POP,
                0,
                2,
                Opcodes.LOAD_CONST,
                0,
                Opcodes.RETURN_ENDPOINT,
                0
        };
        var pool = new Object[] {"https://fallback.example.com"};
        Bytecode bytecode = result(code, pool);

        assertEquals("https://first.example.com",
                resolve(bytecode, params("https://first.example.com", "https://second.example.com")));
        assertEquals("https://second.example.com",
                resolve(bytecode, params(null, "https://second.example.com")));
        assertEquals("https://fallback.example.com", resolve(bytecode, params(null, null)));
    }

    @Test
    void compilesIteBranches() {
        // ite(reg0, true, false): JMP_IF_FALSE over the true arm, which jumps over the false arm.
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.JMP_IF_FALSE,
                0,
                5,
                Opcodes.LOAD_CONST,
                0, // Boolean.TRUE
                Opcodes.JUMP,
                0,
                2,
                Opcodes.LOAD_CONST,
                1, // Boolean.FALSE
                Opcodes.RETURN_VALUE
        };
        var pool = new Object[] {Boolean.TRUE, Boolean.FALSE};
        Bytecode bytecode = condition(code, pool);

        assertInstanceOf(CompiledNode.Ite.class, bytecode.conditionNode(0));
        assertTrue(evaluate(bytecode, Boolean.TRUE));
        assertFalse(evaluate(bytecode, Boolean.FALSE));
        assertFalse(evaluate(bytecode, null));
    }

    @Test
    void mergesAdjacentConstantTemplateParts() {
        byte[] code = {
                Opcodes.LOAD_CONST,
                0, // "https://"
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LOAD_CONST,
                1, // ".example.com"
                Opcodes.LOAD_CONST,
                2, // "/path"
                Opcodes.RESOLVE_TEMPLATE,
                4,
                Opcodes.RETURN_ENDPOINT,
                0
        };
        var pool = new Object[] {"https://", ".example.com", "/path"};
        Bytecode bytecode = result(code, pool);

        assertEquals("https://bucket.example.com/path", resolve(bytecode, params("bucket", null)));
    }

    @Test
    void rejectsNonStringConstantTemplateParts() {
        byte[] code = {
                Opcodes.LOAD_CONST,
                0,
                Opcodes.RESOLVE_TEMPLATE,
                1,
                Opcodes.RETURN_VALUE
        };
        Bytecode bytecode = result(code, new Object[] {Boolean.TRUE});

        var error = assertThrows(RulesEvaluationError.class, () -> bytecode.resultNode(0));

        assertTrue(
                error.getMessage().contains(
                        "RESOLVE_TEMPLATE constant part must be string, found java.lang.Boolean"),
                error.getMessage());
    }

    @Test
    void buildsAndCachesPrevalidatedUriTemplates() {
        var host = new CompiledNode.Template(
                new String[] {"", ".example.com"},
                new CompiledNode[] {new CompiledNode.Reg(0)});
        var path = new CompiledNode.Template(
                new String[] {"/", ""},
                new CompiledNode[] {new CompiledNode.Reg(1)});
        CompiledNode node = CompiledNode.buildUri("https", host, path);
        assertInstanceOf(CompiledNode.BuildUriTemplate.class, node);

        Bytecode bytecode = condition(
                new byte[] {Opcodes.LOAD_CONST, 0, Opcodes.RETURN_VALUE},
                new Object[] {Boolean.TRUE});
        var evaluator = new BytecodeEvaluator(bytecode, new RulesExtension[0], RegisterFiller.of(bytecode, Map.of()));
        evaluator.reset(Context.create(), params("bucket", "key"));

        Object first = node.eval(evaluator);
        assertEquals("https://bucket.example.com/key", first.toString());
        assertSame(first, node.eval(evaluator), "repeated values should hit the value-keyed URI cache");

        evaluator.registers[0] = "other";
        Object changed = node.eval(evaluator);
        assertNotSame(first, changed);
        assertEquals("https://other.example.com/key", changed.toString());
    }

    @Test
    void fallsBackWhenUriTemplateLiteralsAreInvalid() {
        CompiledNode node = CompiledNode.buildUri(
                "https",
                new CompiledNode.Const("not/a/host"),
                new CompiledNode.Const(""));
        assertInstanceOf(CompiledNode.BuildUri.class, node);
    }

    @Test
    void decodesMap3WithTheSameLayoutAsEveryOtherArity() {
        // The retired interpreter read MAP3's key and value slots in an order inconsistent with MAP1,
        // MAP2, MAP4 and MAPN, producing a map of values keyed by values. Entries are pushed
        // value-then-key, so the keys are the odd slots.
        byte[] code = {
                Opcodes.LOAD_CONST,
                0, // "val-a"
                Opcodes.LOAD_CONST,
                1, // "a"
                Opcodes.LOAD_CONST,
                2, // "val-b"
                Opcodes.LOAD_CONST,
                3, // "b"
                Opcodes.LOAD_CONST,
                4, // "val-c"
                Opcodes.LOAD_CONST,
                5, // "c"
                Opcodes.MAP3,
                Opcodes.LOAD_CONST,
                6, // the URL, popped before the properties
                Opcodes.RETURN_ENDPOINT,
                2 // properties present, headers absent
        };
        var pool = new Object[] {"val-a", "a", "val-b", "b", "val-c", "c", "https://example.com"};

        var capture = new CapturingExtension();
        resolve(result(code, pool), params(null, null), capture);
        assertEquals(Map.of("a", "val-a", "b", "val-b", "c", "val-c"), capture.properties);
    }

    @Test
    void buildsAuthSchemeDirectlyWithoutMaterializingItsPropertyGraph() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0, // auth scheme name
                Opcodes.LOAD_CONST,
                0, // "name"
                Opcodes.STRUCTN,
                1,
                Opcodes.LIST1,
                Opcodes.LOAD_CONST,
                1, // "authSchemes"
                Opcodes.MAP1,
                Opcodes.LOAD_CONST,
                2, // URL
                Opcodes.RETURN_ENDPOINT,
                2
        };
        var bytecode = result(code, new Object[] {"name", "authSchemes", "https://example.com"});
        var extension = new DirectAuthExtension();

        Endpoint endpoint = resolveEndpoint(bytecode, params("aws.auth#test", null), extension);

        assertInstanceOf(CompiledNode.ReturnAuthEndpoint.class, bytecode.resultNode(0));
        assertEquals("aws.auth#test", endpoint.authSchemes().get(0).authSchemeId());
        assertEquals(1, extension.directCalls);
        assertEquals(0, extension.legacyCalls);
        assertEquals(Map.of(), extension.otherProperties);
    }

    @Test
    void passesOtherTerminalPropertiesAlongsideDirectAuthScheme() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0, // auth scheme name
                Opcodes.LOAD_CONST,
                0, // "name"
                Opcodes.STRUCTN,
                1,
                Opcodes.LIST1,
                Opcodes.LOAD_CONST,
                1, // "authSchemes"
                Opcodes.LOAD_REGISTER,
                1, // backend value
                Opcodes.LOAD_CONST,
                2, // "backend"
                Opcodes.MAP2,
                Opcodes.LOAD_CONST,
                3, // URL
                Opcodes.RETURN_ENDPOINT,
                2
        };
        var bytecode = result(
                code,
                new Object[] {"name", "authSchemes", "backend", "https://example.com"});
        var extension = new DirectAuthExtension();

        resolveEndpoint(bytecode, params("aws.auth#test", "S3Express"), extension);

        assertEquals(Map.of("backend", "S3Express"), extension.otherProperties);
    }

    @Test
    void materializesAuthSchemePropertiesForLegacyExtensions() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LOAD_CONST,
                0, // "name"
                Opcodes.STRUCTN,
                1,
                Opcodes.LIST1,
                Opcodes.LOAD_CONST,
                1, // "authSchemes"
                Opcodes.MAP1,
                Opcodes.LOAD_CONST,
                2, // URL
                Opcodes.RETURN_ENDPOINT,
                2
        };
        var bytecode = result(code, new Object[] {"name", "authSchemes", "https://example.com"});
        var extension = new CapturingExtension();

        resolveEndpoint(bytecode, params("aws.auth#test", null), extension);

        var entries = (List<?>) extension.properties.get("authSchemes");
        assertEquals("aws.auth#test", EndpointUtils.getProperty(entries.get(0), "name"));
    }

    @Test
    void reportsMalformedBytecodeInsteadOfDegrading() {
        // With no interpreter to fall back to, an undecodable stream has to be an error. It surfaces on
        // first use because compilation is lazy, which is also when the interpreter would have tripped.
        byte[] code = {
                Opcodes.LOAD_CONST,
                0,
                (byte) 0xFE, // not an opcode
                Opcodes.RETURN_VALUE
        };
        Bytecode bytecode = condition(code, new Object[] {"x"});
        var error = assertThrows(RulesEvaluationError.class, () -> bytecode.conditionNode(0));
        assertTrue(error.getMessage().contains("Cannot compile rules program"), error.getMessage());
    }

    @Test
    void compilesLazilyOnFirstUse() {
        var code = substringEqualsProgram(0, 6, true);
        var pool = new Object[] {"", "--x-s3"};
        Bytecode bytecode = condition(code, pool);

        assertEquals(0, bytecode.getCompiledNodeCount(), "nothing should be compiled before first use");
        evaluate(bytecode, "my-bucket--x-s3");
        assertEquals(1, bytecode.getCompiledNodeCount(), "the condition should compile on first use");
    }

    // -------------------------------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------------------------------

    private static final RegisterDefinition[] REGISTERS = {
            new RegisterDefinition("value", false, null, null, false),
            new RegisterDefinition("other", false, null, null, false)
    };

    /** Captures the properties a result hands to the extensions, which is otherwise not observable. */
    private static final class CapturingExtension implements RulesExtension {
        private Map<String, Object> properties;

        @Override
        public void extractEndpointProperties(
                Endpoint.Builder builder,
                Context context,
                Map<String, Object> properties,
                Map<String, List<String>> headers
        ) {
            this.properties = properties;
        }
    }

    private static final class DirectAuthExtension implements RulesExtension {
        private int directCalls;
        private int legacyCalls;
        private Map<String, Object> otherProperties = Map.of();

        @Override
        public EndpointAuthScheme createEndpointAuthScheme(PropertyGetter authScheme) {
            directCalls++;
            return EndpointAuthScheme.builder()
                    .authSchemeId((String) authScheme.getProperty("name"))
                    .build();
        }

        @Override
        public void extractEndpointProperties(
                Endpoint.Builder builder,
                Context context,
                Map<String, Object> properties,
                Map<String, List<String>> headers
        ) {
            legacyCalls++;
            otherProperties = properties;
        }
    }

    private static Map<String, Object> params(Object value, Object other) {
        var map = new HashMap<String, Object>();
        map.put("value", value);
        map.put("other", other);
        return map;
    }

    private static Bytecode condition(byte[] code, Object[] pool) {
        return build(code, new int[] {0}, new int[0], pool);
    }

    private static Bytecode result(byte[] code, Object[] pool) {
        return build(code, new int[0], new int[] {0}, pool);
    }

    private static Bytecode build(byte[] code, int[] conditionOffsets, int[] resultOffsets, Object[] pool) {
        return new Bytecode(code,
                conditionOffsets,
                resultOffsets,
                REGISTERS,
                pool,
                new RulesFunction[0],
                new int[0],
                1);
    }

    private static boolean evaluate(Bytecode bytecode, Object value) {
        var evaluator = new BytecodeEvaluator(bytecode,
                new RulesExtension[0],
                RegisterFiller.of(bytecode, Map.of()));
        evaluator.reset(Context.create(), params(value, null));
        return evaluator.test(0);
    }

    private static String resolve(Bytecode bytecode, Map<String, Object> parameters) {
        return resolve(bytecode, parameters, null);
    }

    private static String resolve(Bytecode bytecode, Map<String, Object> parameters, RulesExtension extension) {
        Endpoint endpoint = resolveEndpoint(bytecode, parameters, extension);
        return endpoint == null ? null : endpoint.uri().toString();
    }

    private static Endpoint resolveEndpoint(
            Bytecode bytecode,
            Map<String, Object> parameters,
            RulesExtension extension
    ) {
        var extensions = extension == null
                ? new RulesExtension[0]
                : new RulesExtension[] {extension};
        var evaluator = new BytecodeEvaluator(bytecode, extensions, RegisterFiller.of(bytecode, Map.of()));
        evaluator.reset(Context.create(), parameters);
        return evaluator.resolveResult(0);
    }
}
