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
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointContext;

/**
 * Covers {@link NodeCompiler}, which is the only way a rules program is evaluated now that the bytecode
 * interpreter is gone.
 *
 * <p>The broad correctness gate is {@code EndpointRulesetConformanceTest}, which resolves every endpoint
 * test case every AWS ruleset declares. These cases pin down the shapes those rulesets do not happen to
 * contain, including the constants for which {@code substring} plus {@code stringEquals} cannot become a
 * bounded {@code regionMatches}. They verify that those cases still specialize without changing the
 * semantics the rules language defines.
 */
class NodeCompilerTest {

    // LOAD_REGISTER 0; SUBSTRING start end reverse; JNN_OR_POP -> past the ""; LOAD_CONST ""; LOAD_CONST x;
    // STRING_EQUALS; RETURN_VALUE -- the generic encoding of the prefix/suffix test idiom.
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

    private static byte[] substringEqualsOpcodeProgram(int start, int end, boolean reverse) {
        return new byte[] {
                Opcodes.SUBSTRING_EQ,
                0,
                (byte) start,
                (byte) end,
                (byte) (reverse ? 1 : 0),
                0,
                0,
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
    void foldsSubstringEqualsToFalseWhenTheLiteralIsShorterThanTheRange() {
        // substringEquals compares only as many characters of the literal as the range covers, so fusing
        // would report a match for anything whose last six characters merely start with "--".
        var code = substringEqualsProgram(0, 6, true);
        var pool = new Object[] {"", "--"};
        Bytecode bytecode = condition(code, pool);

        assertInstanceOf(CompiledNode.Const.class, bytecode.conditionNode(0));
        assertFalse(evaluate(bytecode, "bucket--x-s3"), "\"--x-s3\" is not equal to \"--\"");
        assertFalse(evaluate(bytecode, "ab--"));
    }

    @Test
    void foldsSubstringEqualsToFalseWhenTheLiteralIsLongerThanTheRange() {
        // Last two characters of "xx--" are "--", which are also the first two of the literal, so a
        // bounded regionMatches would call this a match where stringEquals does not.
        var code = substringEqualsProgram(0, 2, true);
        var pool = new Object[] {"", "--x-s3"};
        Bytecode bytecode = condition(code, pool);

        assertInstanceOf(CompiledNode.Const.class, bytecode.conditionNode(0));
        assertFalse(evaluate(bytecode, "xx--"), "\"--\" is not equal to \"--x-s3\"");
    }

    @Test
    void foldsSubstringEqualsToFalseForNonAsciiLiterals() {
        // getSubstring rejects a range containing non-ASCII and so yields "" through the coalesce, while
        // substringEquals would match the characters.
        var code = substringEqualsProgram(0, 2, false);
        var pool = new Object[] {"", "éé"};
        Bytecode bytecode = condition(code, pool);

        assertInstanceOf(CompiledNode.Const.class, bytecode.conditionNode(0));
        assertFalse(evaluate(bytecode, "éérest"), "a non-ASCII range reads as unmatched");
    }

    @Test
    void specializesSubstringEqualsForAnEmptyLiteral() {
        // A zero-length substring coalesces to "", which equals the empty literal, whereas the fused
        // substringEquals operation reports false. The range length deliberately matches the expected
        // length so this must use the extraction-failure node rather than bounded regionMatches.
        var code = substringEqualsProgram(0, 0, false);
        var pool = new Object[] {"", ""};
        Bytecode bytecode = condition(code, pool);

        assertInstanceOf(CompiledNode.SubstringEmptyReg.class, bytecode.conditionNode(0));
        assertTrue(evaluate(bytecode, "anything"), "a zero-length substring coalesces to \"\"");
    }

    @Test
    void specializesSubstringEqualsOpcodeWithoutChangingCoalesceSemantics() {
        Bytecode direct = condition(substringEqualsOpcodeProgram(0, 6, true), new Object[] {"--x-s3"});
        assertInstanceOf(CompiledNode.SubstringEqReg.class, direct.conditionNode(0));
        assertTrue(evaluate(direct, "bucket--x-s3"));

        Bytecode wrongLength = condition(substringEqualsOpcodeProgram(0, 6, true), new Object[] {"--"});
        assertInstanceOf(CompiledNode.Const.class, wrongLength.conditionNode(0));
        assertFalse(evaluate(wrongLength, "bucket--x-s3"));

        Bytecode nonAscii = condition(substringEqualsOpcodeProgram(0, 2, false), new Object[] {"éé"});
        assertInstanceOf(CompiledNode.Const.class, nonAscii.conditionNode(0));
        assertFalse(evaluate(nonAscii, "éérest"));

        Bytecode empty = condition(substringEqualsOpcodeProgram(0, 2, false), new Object[] {""});
        assertInstanceOf(CompiledNode.SubstringEmptyReg.class, empty.conditionNode(0));
        assertFalse(evaluate(empty, "ab"));
        assertTrue(evaluate(empty, "éé"));
        assertTrue(evaluate(empty, "a"));
        assertTrue(evaluate(empty, null));
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
        bind(evaluator, Context.create(), params("example.com", Boolean.TRUE));
        assertTrue(evaluator.test(0));
        bind(evaluator, Context.create(), params("example.com", Boolean.FALSE));
        assertFalse(evaluator.test(0));
        bind(evaluator, Context.create(), params("example.com", "true"));
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
                error.getMessage()
                        .contains(
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
    void rejectsIteWhoseTrueArmOnlyHasANestedCoalesceJump() {
        // The true arm has no JUMP to the ITE join. Its nested coalesce fallback does, but that target
        // belongs only to the recursive fallback decode and must not be reused as the ITE end target.
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.JMP_IF_FALSE,
                0,
                10,
                Opcodes.LOAD_CONST,
                0,
                Opcodes.JNN_OR_POP,
                0,
                5,
                Opcodes.LOAD_CONST,
                1,
                Opcodes.JUMP,
                0,
                3,
                Opcodes.LOAD_CONST,
                2,
                Opcodes.RETURN_VALUE
        };
        Bytecode bytecode = condition(code, new Object[] {"preferred", "fallback", "false"});

        assertThrows(RulesEvaluationError.class, () -> bytecode.conditionNode(0));
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
                error.getMessage()
                        .contains(
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
        bind(evaluator, Context.create(), params("bucket", "key"));

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
    void buildUriAcceptsANullPath() {
        CompiledNode node = new CompiledNode.BuildUri(
                "https",
                new CompiledNode.Const("example.com"),
                new CompiledNode.Const(null));
        Bytecode bytecode = condition(
                new byte[] {Opcodes.LOAD_CONST, 0, Opcodes.RETURN_VALUE},
                new Object[] {Boolean.TRUE});
        var evaluator = new BytecodeEvaluator(bytecode, new RulesExtension[0], RegisterFiller.of(bytecode, Map.of()));

        assertEquals("https://example.com", node.eval(evaluator).toString());
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
    void sharesAConstantPropertyBagAcrossResolutions() {
        // Nothing in the bag is interpolated, so the plan materializes it once at load time and every
        // resolution hands the extension the very same object.
        byte[] code = {
                Opcodes.LOAD_CONST,
                0, // "aws.auth#test"
                Opcodes.LOAD_CONST,
                1, // "name"
                Opcodes.STRUCTN,
                1,
                Opcodes.LIST1,
                Opcodes.LOAD_CONST,
                2, // "authSchemes"
                Opcodes.MAP1,
                Opcodes.LOAD_CONST,
                3, // URL
                Opcodes.RETURN_ENDPOINT,
                2
        };
        var pool = new Object[] {"aws.auth#test", "name", "authSchemes", "https://example.com"};
        var bytecode = result(code, pool);
        var capture = new CapturingExtension();

        resolveEndpoint(bytecode, params(null, null), capture);
        Map<String, Object> first = capture.properties;
        resolveEndpoint(bytecode, params(null, null), capture);

        assertSame(first, capture.properties);
        assertEquals("aws.auth#test",
                EndpointUtils.getProperty(((List<?>) first.get("authSchemes")).getFirst(), "name"));
    }

    @Test
    void readsAnInterpolatedPropertyThroughLazyViews() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0, // auth scheme name, per resolution
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
        var capture = new CapturingExtension();

        resolveEndpoint(bytecode, params("aws.auth#first", null), capture);
        var schemes = (List<?>) capture.properties.get("authSchemes");

        assertEquals(1, schemes.size());
        assertEquals("aws.auth#first", EndpointUtils.getProperty(schemes.getFirst(), "name"));
        assertEquals(1, capture.properties.size());
        assertTrue(capture.properties.containsKey("authSchemes"));

        resolveEndpoint(bytecode, params("aws.auth#second", null), capture);
        assertEquals("aws.auth#second",
                EndpointUtils.getProperty(
                        ((List<?>) capture.properties.get("authSchemes")).getFirst(),
                        "name"));
    }

    @Test
    void viewsOverEqualValuesAreEqualSoContentKeyedCachesStillHit() {
        // AwsRulesExtension memoizes typed auth schemes keyed on the raw authSchemes list, so the views
        // must compare by value rather than by identity or that cache would miss on every resolution.
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
                2,
                Opcodes.RETURN_ENDPOINT,
                2
        };
        var bytecode = result(code, new Object[] {"name", "authSchemes", "https://example.com"});
        var capture = new CapturingExtension();

        resolveEndpoint(bytecode, params("aws.auth#test", null), capture);
        Object first = capture.properties.get("authSchemes");
        resolveEndpoint(bytecode, params("aws.auth#test", null), capture);
        Object second = capture.properties.get("authSchemes");

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
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
    void projectsDynamicPropertiesDirectlyOntoTheEndpoint() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LOAD_CONST,
                0, // "backend"
                Opcodes.MAP1,
                Opcodes.LOAD_CONST,
                1, // URL
                Opcodes.RETURN_ENDPOINT,
                2
        };
        var bytecode = result(code, new Object[] {"backend", "https://example.com"});
        var extension = new DirectPropertyExtension();
        var evaluator = new BytecodeEvaluator(
                bytecode,
                new RulesExtension[] {new StdExtension(), extension},
                RegisterFiller.of(bytecode, Map.of()));

        bind(evaluator, Context.create(), params("first", null));
        Endpoint first = evaluator.resolveResult(0);
        bind(evaluator, Context.create(), params("second", null));
        Endpoint second = evaluator.resolveResult(0);

        assertInstanceOf(ProjectedEndpoint.Endpoint1.class, first);
        assertEquals("first", first.property(DirectPropertyExtension.BACKEND));
        assertEquals("second", second.property(DirectPropertyExtension.BACKEND));
        assertEquals(Set.of(DirectPropertyExtension.BACKEND), first.properties());
        assertEquals(1, extension.bindCalls);
        assertEquals(0, extension.materializedCalls);
    }

    @Test
    void sharesBindingsForInternedPropertyShapes() {
        byte[] result = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LOAD_CONST,
                0, // "backend"
                Opcodes.MAP1,
                Opcodes.LOAD_CONST,
                1, // URL
                Opcodes.RETURN_ENDPOINT,
                2
        };
        byte[] code = new byte[result.length * 2];
        System.arraycopy(result, 0, code, 0, result.length);
        System.arraycopy(result, 0, code, result.length, result.length);
        var bytecode = build(
                code,
                new int[0],
                new int[] {0, result.length},
                new Object[] {"backend", "https://example.com"});
        var extension = new DirectPropertyExtension();
        var evaluator = new BytecodeEvaluator(
                bytecode,
                new RulesExtension[] {extension},
                RegisterFiller.of(bytecode, Map.of()));

        bind(evaluator, Context.create(), params("first", null));
        assertEquals("first", evaluator.resolveResult(0).property(DirectPropertyExtension.BACKEND));
        assertEquals("first", evaluator.resolveResult(1).property(DirectPropertyExtension.BACKEND));
        assertEquals(1, extension.bindCalls);
    }

    @Test
    void projectsDynamicHeadersOntoTheEndpoint() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LIST1,
                Opcodes.LOAD_CONST,
                0, // "x-test"
                Opcodes.MAP1,
                Opcodes.LOAD_CONST,
                1, // URL
                Opcodes.RETURN_ENDPOINT,
                1 // headers present, properties absent
        };
        var bytecode = result(code, new Object[] {"x-test", "https://example.com"});
        var evaluator = new BytecodeEvaluator(
                bytecode,
                new RulesExtension[0],
                RegisterFiller.of(bytecode, Map.of()));

        bind(evaluator, Context.create(), params("first", null));
        Endpoint endpoint = evaluator.resolveResult(0);

        assertInstanceOf(ProjectedEndpoint.Endpoint1.class, endpoint);
        assertEquals(Map.of("x-test", List.of("first")), endpoint.property(EndpointContext.HEADERS));
        assertEquals(Set.of(EndpointContext.HEADERS), endpoint.properties());
    }

    @Test
    void preservesDynamicHeadersForMaterializedExtensions() {
        byte[] code = {
                Opcodes.LOAD_REGISTER,
                0,
                Opcodes.LIST1,
                Opcodes.LOAD_CONST,
                0, // "x-test"
                Opcodes.MAP1,
                Opcodes.LOAD_CONST,
                1, // URL
                Opcodes.RETURN_ENDPOINT,
                1 // headers present, properties absent
        };
        var bytecode = result(code, new Object[] {"x-test", "https://example.com"});
        var extension = new CapturingExtension();

        Endpoint endpoint = resolveEndpoint(bytecode, params("first", null), extension);

        assertEquals(Map.of("x-test", List.of("first")), extension.headers);
        assertEquals(extension.headers, endpoint.property(EndpointContext.HEADERS));
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
        private Map<String, List<String>> headers;

        @Override
        public void extractEndpointProperties(
                Endpoint.Builder builder,
                Context context,
                Map<String, Object> properties,
                Map<String, List<String>> headers
        ) {
            this.properties = properties;
            this.headers = headers;
        }
    }

    private static final class DirectPropertyExtension implements RulesExtension {
        private static final Context.Key<String> BACKEND = Context.key("test backend");
        private int bindCalls;
        private int materializedCalls;

        @Override
        public EndpointPropertyBinding bindEndpointProperties(EndpointPropertyBinding.Properties properties) {
            EndpointPropertyBinding.Properties backend = properties.get("backend");
            if (backend == null) {
                return null;
            }
            bindCalls++;
            return new EndpointPropertyBinding() {
                @Override
                public Set<Context.Key<?>> properties() {
                    return Set.of(BACKEND);
                }

                @Override
                public Object property(Context.Key<?> property, ResolutionValues values) {
                    return property == BACKEND ? backend.resolve(values) : null;
                }
            };
        }

        @Override
        public void extractEndpointProperties(
                Endpoint.Builder builder,
                Context context,
                Map<String, Object> properties,
                Map<String, List<String>> headers
        ) {
            materializedCalls++;
        }
    }

    /**
     * Binds parameters the way {@link BytecodeEndpointResolver} does, so these tests exercise the same
     * register setup a real resolution performs rather than a test-only shortcut.
     */
    private static void bind(BytecodeEvaluator evaluator, Context context, Map<String, Object> parameters) {
        evaluator.prepareRegisters(context);
        evaluator.registerSink.putAll(parameters);
        evaluator.finishRegisters(context);
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
        bind(evaluator, Context.create(), params(value, null));
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
        bind(evaluator, Context.create(), parameters);
        return evaluator.resolveResult(0);
    }
}
