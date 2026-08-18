/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;

/**
 * Generates a field-based Java endpoint resolver from compiled rules-engine bytecode.
 */
public final class JavaEndpointResolverGenerator {

    private final Bytecode bytecode;
    private final byte[] instructions;
    private final StringBuilder source = new StringBuilder();
    private final Set<Integer> structureSizes = new HashSet<>();
    private final Set<Integer> uriTemplateSizes = new HashSet<>();
    private final Set<Integer> listSizes = new HashSet<>();
    private final Set<Integer> mapSizes = new HashSet<>();
    private final Set<Integer> structuredRegisters = new HashSet<>();
    private final Map<Integer, SubstringBinding> substringBindings = new HashMap<>();

    /**
     * @param bytecode compiled endpoint rules program
     */
    public JavaEndpointResolverGenerator(Bytecode bytecode) {
        this.bytecode = bytecode;
        this.instructions = bytecode.getBytecode();
    }

    /**
     * Generates a complete Java compilation unit.
     *
     * @param packageName package for the generated resolver
     * @param className simple class name
     * @return Java source
     */
    public String generate(String packageName, String className) {
        return generate(packageName, className, className + ".bdd");
    }

    /**
     * Generates a complete Java compilation unit that loads its program from a binary resource.
     *
     * @param packageName package for the generated resolver
     * @param className simple class name
     * @param programResource class-relative or absolute binary resource name
     * @return Java source
     */
    public String generate(String packageName, String className, String programResource) {
        validateName(packageName, false);
        validateName(className, true);
        if (programResource == null || programResource.isEmpty()) {
            throw new IllegalArgumentException("Program resource must not be empty");
        }
        source.setLength(0);
        structureSizes.clear();
        uriTemplateSizes.clear();
        listSizes.clear();
        mapSizes.clear();
        structuredRegisters.clear();
        substringBindings.clear();
        analyzePrograms();

        line("package " + packageName + ";");
        line("");
        line("@SuppressWarnings({\"unchecked\", \"rawtypes\"})");
        line("public final class " + className + " extends "
                + "software.amazon.smithy.java.rulesengine.GeneratedEndpointResolver<" + className + ".State> {");
        writeConstants();
        writeFunctionFields();
        writeConstructor(className, programResource);
        writeParameters();
        writeState();
        writeLifecycle();
        writeEvaluate();
        writeProgramMethods();
        writeHelpers();
        line("}");
        return source.toString();
    }

    private void writeConstants() {
        Object[] constants = bytecode.getConstantPool();
        for (int i = 0; i < constants.length; i++) {
            Object value = constants[i];
            line("");
            line("    private static final " + constantType(value) + " C" + i + " = " + constantValue(value) + ";");
        }
    }

    private void writeFunctionFields() {
        for (int i = 0; i < bytecode.getFunctions().length; i++) {
            line("");
            line("    private final software.amazon.smithy.java.rulesengine.RulesFunction f" + i + ";");
        }
    }

    private void writeConstructor(String className, String programResource) {
        line("");
        line("    public " + className + "() {");
        line("        super(" + className + ".class, " + quote(programResource) + ");");
        for (int i = 0; i < bytecode.getFunctions().length; i++) {
            line("        this.f" + i + " = function(" + i + ");");
        }
        line("    }");
    }

    private void writeState() {
        RegisterDefinition[] registers = bytecode.getRegisterDefinitions();
        line("");
        line("    static class State extends "
                + "software.amazon.smithy.java.rulesengine.GeneratedEndpointResolver.EvaluationState {");
        for (int i = 0; i < registers.length; i++) {
            line("        " + registerType(registers[i]) + " r" + i + ";");
            if (!registers[i].temp()) {
                line("        boolean p" + i + ";");
            }
            if (registers[i].builtin() != null) {
                line("        boolean l" + i + ";");
            }
        }
        line("        software.amazon.smithy.java.context.Context evaluationContext;");
        line("        final java.util.Map<String, Object> traceParameters = new java.util.AbstractMap<>() {");
        line("            @Override");
        line("            public Object get(Object name) {");
        for (int i = 0; i < registers.length; i++) {
            if (!registers[i].temp()) {
                line("                if (" + quote(registers[i].name()) + ".equals(name)) return r" + i + ";");
            }
        }
        line("                return null;");
        line("            }");
        line("");
        line("            @Override");
        line("            public java.util.Set<Entry<String, Object>> entrySet() {");
        line("                var result = new java.util.LinkedHashSet<Entry<String, Object>>();");
        for (int i = 0; i < registers.length; i++) {
            if (!registers[i].temp()) {
                line("                if (r" + i + " != null) result.add(java.util.Map.entry("
                        + quote(registers[i].name()) + ", r" + i + "));");
            }
        }
        line("                return result;");
        line("            }");
        line("        };");
        line("");
        line("        State(software.amazon.smithy.java.rulesengine.RulesExtension[] extensions) {");
        line("            super(extensions);");
        line("        }");
        line("");
        line("        @Override");
        line("        public void put(String name, Object value) {");
        line("            switch (name) {");
        for (int i = 0; i < registers.length; i++) {
            if (!registers[i].temp()) {
                line("                case " + quote(registers[i].name()) + " -> { r" + i
                        + " = " + castRegisterValue(registers[i], "value") + "; p" + i + " = true; }");
            }
        }
        line("                default -> { }");
        line("            }");
        line("        }");
        line("    }");
    }

    private void writeParameters() {
        RegisterDefinition[] registers = bytecode.getRegisterDefinitions();
        line("");
        line("    public static final class Parameters extends State implements "
                + "software.amazon.smithy.java.rulesengine.GeneratedEndpointResolver.GeneratedParameters {");
        line("        private Parameters(software.amazon.smithy.java.rulesengine.RulesExtension[] extensions) {");
        line("            super(extensions);");
        for (int i = 0; i < registers.length; i++) {
            RegisterDefinition register = registers[i];
            if (!register.temp()) {
                line("            r" + i + " = " + registerInitialValue(register, true) + ";");
                line("            p" + i + " = " + (register.defaultValue() != null) + ";");
            }
        }
        line("        }");
        line("    }");
    }

    private void writeLifecycle() {
        RegisterDefinition[] registers = bytecode.getRegisterDefinitions();
        line("");
        line("    @Override");
        line("    public software.amazon.smithy.java.rulesengine.GeneratedEndpointResolver.GeneratedParameters "
                + "createParameters() {");
        line("        return new Parameters(extensions());");
        line("    }");
        line("");
        line("    @Override");
        line("    protected State directState("
                + "software.amazon.smithy.java.rulesengine.GeneratedEndpointResolver.GeneratedParameters values) {");
        line("        return values instanceof Parameters parameters ? parameters : null;");
        line("    }");
        line("");
        line("    @Override");
        line("    protected State createState() {");
        line("        return new State(extensions());");
        line("    }");
        line("");
        line("    @Override");
        line("    protected java.util.Map<String, Object> traceParameters(State state) {");
        line("        return state.traceParameters;");
        line("    }");
        line("");
        line("    @Override");
        line("    protected void initialize(State state) {");
        for (int i = 0; i < registers.length; i++) {
            RegisterDefinition register = registers[i];
            if (register.temp()) {
                continue;
            }
            String value = register.builtin() == null
                    ? registerInitialValue(register, true)
                    : registerInitialValue(register, false);
            line("        state.r" + i + " = " + value + ";");
            line("        state.p" + i + " = " + (register.builtin() == null
                    && register.defaultValue() != null) + ";");
        }
        line("    }");
        line("");
        line("    @Override");
        line("    protected void initialize(State state, "
                + "software.amazon.smithy.java.rulesengine.GeneratedEndpointResolver.GeneratedParameters values) {");
        line("        Parameters parameters = (Parameters) values;");
        for (int i = 0; i < registers.length; i++) {
            RegisterDefinition register = registers[i];
            if (!register.temp()) {
                line("        state.r" + i + " = parameters.r" + i + ";");
                line("        state.p" + i + " = parameters.p" + i + ";");
            }
        }
        line("    }");
        line("");
        line("    @Override");
        line("    protected void finish(State state, software.amazon.smithy.java.context.Context context) {");
        line("        state.evaluationContext = context;");
        for (int i = 0; i < registers.length; i++) {
            RegisterDefinition register = registers[i];
            if (register.builtin() != null) {
                line("        state.l" + i + " = false;");
            }
        }
        for (int i = 0; i < registers.length; i++) {
            RegisterDefinition register = registers[i];
            if (register.required() && register.defaultValue() == null
                    && register.builtin() == null
                    && !register.temp()) {
                line("        if (!state.p" + i + ") {");
                line("            throw new software.amazon.smithy.java.rulesengine.RulesEvaluationError("
                        + quote("Missing required parameter: " + register.name()) + ");");
                line("        }");
            }
        }
        line("    }");
        for (int i = 0; i < registers.length; i++) {
            RegisterDefinition register = registers[i];
            if (register.builtin() == null) {
                continue;
            }
            line("");
            line("    private " + registerType(register) + " r" + i + "(State state) {");
            line("        if (!state.p" + i + " && !state.l" + i + ") {");
            line("            state.r" + i + " = "
                    + castRegisterValue(register, "builtin(" + i + ", state.evaluationContext)") + ";");
            line("            state.l" + i + " = true;");
            line("        }");
            line("        return state.r" + i + ";");
            line("    }");
        }
    }

    private void writeEvaluate() {
        line("");
        line("    @Override");
        line("    protected software.amazon.smithy.java.endpoints.Endpoint evaluate(State state) {");
        line("        return " + referenceExpression(bytecode.getBddRootRef()) + ";");
        line("    }");
        line("");
        line("    @Override");
        line("    protected software.amazon.smithy.java.endpoints.Endpoint evaluateTraced(State state,");
        line("            software.amazon.smithy.java.rulesengine.BddTrace trace) {");
        line("        int ref = bddRootRef();");
        line("        int[] nodes = bddNodes();");
        line("        while ((ref > 1 && ref < " + Bdd.RESULT_OFFSET + ")");
        line("                || (ref < -1 && ref > -" + Bdd.RESULT_OFFSET + ")) {");
        line("            int index = ref > 0 ? ref - 1 : -ref - 1;");
        line("            int base = index * 3;");
        line("            int condition = nodes[base];");
        line("            boolean satisfied = tracedCondition(state, condition);");
        line("            boolean branch = satisfied ^ (ref < 0);");
        line("            trace.node(ref, condition, satisfied, branch);");
        line("            ref = branch ? nodes[base + 1] : nodes[base + 2];");
        line("        }");
        line("        int resultId = ref == 1 || ref == -1 ? -1 : ref - " + Bdd.RESULT_OFFSET + ";");
        line("        software.amazon.smithy.java.endpoints.Endpoint endpoint = resultId < 0");
        line("                ? null");
        line("                : tracedResult(state, resultId);");
        line("        trace.result(resultId, endpoint);");
        line("        return endpoint;");
        line("    }");
        line("");
        line("    private boolean tracedCondition(State state, int condition) {");
        if (bytecode.getConditionCount() == 0) {
            line("        throw invalidPc(condition);");
        } else {
            line("        return switch (condition) {");
            for (int i = 0; i < bytecode.getConditionCount(); i++) {
                line("            case " + i + " -> " + conditionExpression(i, false) + ";");
            }
            line("            default -> throw invalidPc(condition);");
            line("        };");
        }
        line("    }");
        line("");
        line("    private software.amazon.smithy.java.endpoints.Endpoint tracedResult(State state, int result) {");
        if (bytecode.getResultCount() == 0) {
            line("        throw invalidPc(result);");
        } else {
            line("        return switch (result) {");
            for (int i = 0; i < bytecode.getResultCount(); i++) {
                line("            case " + i + " -> (software.amazon.smithy.java.endpoints.Endpoint) result"
                        + i + "(state);");
            }
            line("            default -> throw invalidPc(result);");
            line("        };");
        }
        line("    }");

        int[] nodes = bytecode.getBddNodes();
        Map<Integer, Integer> incoming = new HashMap<>();
        Set<Integer> reachable = new HashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        int root = bytecode.getBddRootRef();
        pending.add(root);
        while (!pending.isEmpty()) {
            int ref = pending.removeFirst();
            if (!isNodeReference(ref) || !reachable.add(ref)) {
                continue;
            }
            int base = (Math.abs(ref) - 1) * 3;
            int high = nodes[base + 1];
            int low = nodes[base + 2];
            if (isNodeReference(high)) {
                incoming.merge(high, 1, Integer::sum);
                pending.add(high);
            }
            if (isNodeReference(low)) {
                incoming.merge(low, 1, Integer::sum);
                pending.add(low);
            }
        }

        Set<Integer> helpers = new HashSet<>();
        helpers.add(root);
        for (Map.Entry<Integer, Integer> entry : incoming.entrySet()) {
            if (entry.getValue() > 1) {
                helpers.add(entry.getKey());
            }
        }

        List<Integer> sortedHelpers = new ArrayList<>(helpers);
        sortedHelpers.sort((left, right) -> {
            int result = Integer.compare(Math.abs(left), Math.abs(right));
            return result != 0 ? result : Integer.compare(left, right);
        });
        for (int ref : sortedHelpers) {
            line("");
            line("    private software.amazon.smithy.java.endpoints.Endpoint " + nodeMethod(ref) + "(State state) {");
            line("        return " + inlineReferenceExpression(ref, helpers, nodes, "                ", true) + ";");
            line("    }");
        }
    }

    private String inlineReferenceExpression(
            int ref,
            Set<Integer> helpers,
            int[] nodes,
            String indent,
            boolean expandHelper
    ) {
        if (!isNodeReference(ref) || helpers.contains(ref) && !expandHelper) {
            return referenceExpression(ref);
        }
        int base = (Math.abs(ref) - 1) * 3;
        String nestedIndent = indent + "        ";
        return conditionExpression(nodes[base], ref < 0)
                + "\n" + indent + "? " + inlineReferenceExpression(
                        nodes[base + 1], helpers, nodes, nestedIndent, false)
                + "\n" + indent + ": " + inlineReferenceExpression(
                        nodes[base + 2], helpers, nodes, nestedIndent, false);
    }

    private static boolean isNodeReference(int ref) {
        return (ref > 1 && ref < Bdd.RESULT_OFFSET)
                || (ref < -1 && ref > -Bdd.RESULT_OFFSET);
    }

    private static String nodeMethod(int ref) {
        return "node" + (ref > 0 ? "P" : "N") + Math.abs(ref);
    }

    private String conditionExpression(int condition, boolean negated) {
        String expression = switch (bytecode.conditionTypes[condition]) {
            case Bytecode.COND_ISSET -> registerExpression(bytecode.conditionOperands[condition]) + " != null";
            case Bytecode.COND_IS_TRUE ->
                registerExpression(bytecode.conditionOperands[condition]) + " == Boolean.TRUE";
            case Bytecode.COND_IS_FALSE ->
                registerExpression(bytecode.conditionOperands[condition]) + " == Boolean.FALSE";
            case Bytecode.COND_NOT_SET -> registerExpression(bytecode.conditionOperands[condition]) + " == null";
            case Bytecode.COND_STRING_EQ_REG_CONST -> {
                int packed = bytecode.conditionOperands[condition];
                int register = packed & 0xff;
                int constant = packed >>> 8;
                String value = registerExpression(register);
                yield value + " != null && " + value + ".equals(C" + constant + ")";
            }
            default -> {
                String direct = directConditionExpression(condition);
                ConditionBinding binding = conditionBinding(condition);
                yield direct != null
                        ? direct
                        : binding != null
                                ? bindingExpression(binding)
                                : "truthy(condition" + condition + "(state))";
            }
        };
        return negated ? "!(" + expression + ")" : expression;
    }

    private String bindingExpression(ConditionBinding binding) {
        String assignment = "(state.r" + binding.targetRegister + " = " + binding.expression + ")";
        return binding.alwaysTruthy
                ? assignment + " != null"
                : assignment + " != null && state.r" + binding.targetRegister + " != Boolean.FALSE";
    }

    private String directConditionExpression(int condition) {
        int start = bytecode.getConditionStartOffset(condition);
        Program program = readProgram(start);
        String optimized = optimizedProgramExpression(program, start);
        if (optimized != null) {
            return optimized;
        }
        List<Instruction> body = new ArrayList<>(program.instructions.values());
        if (body.size() == 4
                && body.get(0).opcode == Opcodes.LOAD_REGISTER
                && (body.get(1).opcode == Opcodes.LOAD_CONST || body.get(1).opcode == Opcodes.LOAD_CONST_W)
                && body.get(2).opcode == Opcodes.FN2
                && body.get(3).opcode == Opcodes.RETURN_VALUE
                && "aws.isVirtualHostableS3Bucket".equals(
                        bytecode.getFunctions()[body.get(2).operands[0]].getFunctionName())) {
            return "isVirtualHostableBucket((String) " + registerExpression(body.get(0).operands[0])
                    + ", C" + body.get(1).operands[0] + " == Boolean.TRUE)";
        }
        Instruction instruction = program.instructions.get(start);
        Instruction result = instruction == null ? null : program.instructions.get(instruction.next());
        if (result == null || result.opcode != Opcodes.RETURN_VALUE) {
            return null;
        }
        if (instruction.opcode == Opcodes.SUBSTRING_EQ) {
            int[] operands = instruction.operands;
            return substringEqualsExpression(
                    operands[0], operands[1], operands[2], (operands[3] & 1) != 0, operands[4]);
        }
        return null;
    }

    private ConditionBinding conditionBinding(int condition) {
        Program program = readProgram(bytecode.getConditionStartOffset(condition));
        BooleanSelection selection = coalescedBooleanSelection(program);
        if (selection != null) {
            String source = registerExpression(selection.sourceRegister);
            String selected = selection.defaultValue
                    ? source + " == null || " + source + " == Boolean.TRUE"
                    : source + " == Boolean.TRUE";
            return new ConditionBinding(
                    selection.targetRegister,
                    selected + " ? C" + selection.trueConstant + " : C" + selection.falseConstant,
                    true);
        }
        if (program.hasControlFlow) {
            return null;
        }

        List<Instruction> body = new ArrayList<>(program.instructions.values());
        if (body.size() == 2 && body.get(1).opcode == Opcodes.SET_REG_RETURN) {
            Instruction value = body.get(0);
            if (value.opcode == Opcodes.SELECT_BOOL_REG) {
                String source = registerExpression(value.operands[0]);
                return new ConditionBinding(
                        body.get(1).operands[0],
                        source + " != null && " + source
                                + " != Boolean.FALSE ? C" + value.operands[1] + " : C" + value.operands[2],
                        true);
            } else if (value.opcode == Opcodes.SPLIT_GET) {
                return new ConditionBinding(
                        body.get(1).operands[0],
                        "state.splitGet((String) " + registerExpression(value.operands[0])
                                + ", C" + value.operands[1] + ", " + (byte) value.operands[2] + ")",
                        false);
            }
        } else if (body.size() == 3
                && body.get(0).opcode == Opcodes.LOAD_REGISTER
                && body.get(1).opcode == Opcodes.FN1
                && body.get(2).opcode == Opcodes.SET_REG_RETURN) {
            return new ConditionBinding(
                    body.get(2).operands[0],
                    "f" + body.get(1).operands[0] + ".apply1("
                            + registerExpression(body.get(0).operands[0]) + ")",
                    false);
        }
        return null;
    }

    private String referenceExpression(int ref) {
        if (ref == 1 || ref == -1) {
            return "null";
        } else if (isNodeReference(ref)) {
            return nodeMethod(ref) + "(state)";
        } else if (ref >= Bdd.RESULT_OFFSET) {
            return "(software.amazon.smithy.java.endpoints.Endpoint) result"
                    + (ref - Bdd.RESULT_OFFSET) + "(state)";
        }
        throw new IllegalArgumentException("Invalid BDD reference: " + ref);
    }

    private void writeProgramMethods() {
        for (int i = 0; i < bytecode.getConditionCount(); i++) {
            if (directConditionExpression(i) != null) {
                continue;
            }
            ConditionBinding binding = conditionBinding(i);
            if (binding == null) {
                writeProgramMethod(
                        "condition" + i,
                        bytecode.getConditionStartOffset(i),
                        substringBindings.get(i));
            }
        }
        for (int i = 0; i < bytecode.getResultCount(); i++) {
            writeProgramMethod("result" + i, bytecode.getResultOffset(i), null);
        }
    }

    private void writeProgramMethod(String name, int start, SubstringBinding substringBinding) {
        Program program = readProgram(start);
        line("");
        line("    private Object " + name + "(State state) {");
        if (substringBinding != null) {
            line("        state.r" + substringBinding.targetRegister
                    + " = " + substringEqualsExpression(
                            substringBinding.sourceRegister,
                            substringBinding.start,
                            substringBinding.end,
                            substringBinding.reverse,
                            substringBinding.expectedConstant)
                    + " ? C" + substringBinding.expectedConstant + " : null;");
            line("        return state.r" + substringBinding.targetRegister + ";");
            line("    }");
            return;
        }
        BooleanSelection booleanSelection = coalescedBooleanSelection(program);
        if (booleanSelection != null) {
            String source = registerExpression(booleanSelection.sourceRegister);
            String selected = booleanSelection.defaultValue
                    ? source + " == null || " + source + " == Boolean.TRUE"
                    : source + " == Boolean.TRUE";
            line("        state.r" + booleanSelection.targetRegister + " = " + selected
                    + " ? C" + booleanSelection.trueConstant
                    + " : C" + booleanSelection.falseConstant + ";");
            line("        return state.r" + booleanSelection.targetRegister + ";");
            line("    }");
            return;
        }
        String optimizedExpression = optimizedProgramExpression(program, start);
        if (optimizedExpression != null) {
            line("        return " + optimizedExpression + ";");
            line("    }");
            return;
        }
        if (!program.hasControlFlow && writeExpressionProgram(program)) {
            line("    }");
            return;
        }
        for (int i = 0; i < program.maxStack; i++) {
            line("        Object s" + i + " = null;");
        }

        if (!program.hasControlFlow) {
            writeStraightLineProgram(program);
        } else {
            line("        int pc = " + start + ";");
            line("        while (true) {");
            line("            switch (pc) {");
            for (int leader : program.leaders) {
                line("                case " + leader + " -> {");
                int pc = leader;
                while (true) {
                    Instruction instruction = program.instructions.get(pc);
                    if (instruction == null) {
                        line("                    throw invalidPc(pc);");
                        break;
                    }
                    writeInstruction(instruction, program.depths.get(pc), "                    ");
                    if (instruction.isReturn() || instruction.isControlFlow()) {
                        break;
                    }
                    int next = instruction.next();
                    if (program.leaders.contains(next)) {
                        line("                    pc = " + next + ";");
                        line("                    continue;");
                        break;
                    }
                    pc = next;
                }
                line("                }");
            }
            line("                default -> throw invalidPc(pc);");
            line("            }");
            line("        }");
        }
        line("    }");
    }

    private boolean writeExpressionProgram(Program program) {
        for (Instruction instruction : program.instructions.values()) {
            if (!supportsExpression(instruction.opcode & 0xff)) {
                return false;
            }
        }

        List<Instruction> ordered = new ArrayList<>(program.instructions.values());
        List<String> stack = new ArrayList<>(program.maxStack);
        for (int i = 0; i < ordered.size(); i++) {
            Instruction instruction = ordered.get(i);
            int opcode = instruction.opcode & 0xff;
            int[] operands = instruction.operands;

            AuthSchemeProperties authScheme = authSchemeProperties(program, instruction);
            if (authScheme != null) {
                List<String> auth = popExpressions(stack, 8);
                StringBuilder expression = new StringBuilder("state.authSchemeProperties(");
                if (authScheme.extraEntries == 1) {
                    List<String> extra = popExpressions(stack, 2);
                    expression.append(extra.get(0))
                            .append(", ")
                            .append(asString(extra.get(1)))
                            .append(", ");
                }
                for (int entry = 0; entry < 4; entry++) {
                    if (entry > 0) {
                        expression.append(", ");
                    }
                    expression.append(auth.get(entry * 2))
                            .append(", ")
                            .append(asString(auth.get(entry * 2 + 1)));
                }
                stack.add(expression.append(")").toString());
                i += 3;
                continue;
            }

            UriTemplate template = uriTemplate(program, instruction);
            if (template != null) {
                int count = operands[0];
                List<String> parts = popExpressions(stack, count);
                StringBuilder expression = new StringBuilder("buildUri")
                        .append(count)
                        .append("(C")
                        .append(template.buildUri.operands[0])
                        .append(", (String) C")
                        .append(template.path.operands[0]);
                for (String part : parts) {
                    expression.append(", ").append(asString(part));
                }
                stack.add(expression.append(")").toString());
                i += 2;
                continue;
            }

            switch (opcode) {
                case Opcodes.LOAD_CONST, Opcodes.LOAD_CONST_W -> stack.add("C" + operands[0]);
                case Opcodes.LOAD_REGISTER -> stack.add(registerExpression(operands[0]));
                case Opcodes.NOT -> {
                    String value = popExpression(stack);
                    stack.add("Boolean.valueOf(" + value + " == Boolean.FALSE)");
                }
                case Opcodes.ISSET -> {
                    String value = popExpression(stack);
                    stack.add("Boolean.valueOf(" + value + " != null)");
                }
                case Opcodes.TEST_REGISTER_ISSET ->
                    stack.add("Boolean.valueOf(" + registerExpression(operands[0]) + " != null)");
                case Opcodes.TEST_REGISTER_NOT_SET ->
                    stack.add("Boolean.valueOf(" + registerExpression(operands[0]) + " == null)");
                case Opcodes.LIST0 -> stack.add("java.util.List.of()");
                case Opcodes.LIST1 -> stack.add("java.util.List.of(" + popExpression(stack) + ")");
                case Opcodes.LIST2 -> {
                    List<String> values = popExpressions(stack, 2);
                    stack.add("java.util.List.of(" + String.join(", ", values) + ")");
                }
                case Opcodes.LISTN -> {
                    int count = operands[0];
                    List<String> values = popExpressions(stack, count);
                    listSizes.add(count);
                    stack.add("list" + count + "(" + String.join(", ", values) + ")");
                }
                case Opcodes.MAP0 -> stack.add("java.util.Map.of()");
                case Opcodes.MAP1 -> {
                    List<String> entries = popExpressions(stack, 2);
                    mapSizes.add(1);
                    stack.add("map1(" + entries.get(0) + ", " + asString(entries.get(1)) + ")");
                }
                case Opcodes.MAP2, Opcodes.MAP3, Opcodes.MAP4, Opcodes.MAPN -> {
                    int count = mapSize(instruction);
                    List<String> entries = popExpressions(stack, count * 2);
                    mapSizes.add(count);
                    StringBuilder expression = new StringBuilder("map").append(count).append("(");
                    for (int entry = 0; entry < count; entry++) {
                        if (entry > 0) {
                            expression.append(", ");
                        }
                        expression.append(entries.get(entry * 2))
                                .append(", ")
                                .append(asString(entries.get(entry * 2 + 1)));
                    }
                    stack.add(expression.append(")").toString());
                }
                case Opcodes.STRUCTN -> {
                    int count = operands[0];
                    List<String> entries = popExpressions(stack, count * 2);
                    structureSizes.add(count);
                    StringBuilder expression = new StringBuilder("new Struct").append(count).append("(");
                    for (int entry = 0; entry < count; entry++) {
                        if (entry > 0) {
                            expression.append(", ");
                        }
                        expression.append(entries.get(entry * 2))
                                .append(", ")
                                .append(asString(entries.get(entry * 2 + 1)));
                    }
                    stack.add(expression.append(")").toString());
                }
                case Opcodes.RESOLVE_TEMPLATE -> {
                    List<String> parts = popExpressions(stack, operands[0]);
                    StringBuilder expression = new StringBuilder();
                    for (String part : parts) {
                        if (!expression.isEmpty()) {
                            expression.append(" + ");
                        }
                        expression.append(asString(part));
                    }
                    stack.add(expression.isEmpty() ? "\"\"" : expression.toString());
                }
                case Opcodes.FN0 -> stack.add("f" + operands[0] + ".apply0()");
                case Opcodes.FN1 -> {
                    String value = popExpression(stack);
                    stack.add("f" + operands[0] + ".apply1(" + value + ")");
                }
                case Opcodes.FN2 -> {
                    List<String> arguments = popExpressions(stack, 2);
                    if ("aws.isVirtualHostableS3Bucket".equals(
                            bytecode.getFunctions()[operands[0]].getFunctionName())) {
                        stack.add("Boolean.valueOf(isVirtualHostableBucket("
                                + asString(arguments.get(0)) + ", " + arguments.get(1) + " == Boolean.TRUE))");
                    } else {
                        stack.add("f" + operands[0] + ".apply2("
                                + arguments.get(0) + ", " + arguments.get(1) + ")");
                    }
                }
                case Opcodes.FN3 -> {
                    List<String> arguments = popExpressions(stack, 3);
                    stack.add("f" + operands[0] + ".apply(" + String.join(", ", arguments) + ")");
                }
                case Opcodes.FN -> {
                    int count = bytecode.getFunctions()[operands[0]].getArgumentCount();
                    List<String> arguments = popExpressions(stack, count);
                    stack.add("f" + operands[0] + ".apply(" + String.join(", ", arguments) + ")");
                }
                case Opcodes.GET_PROPERTY -> {
                    String value = popExpression(stack);
                    stack.add("state.getProperty(" + value + ", C" + operands[0] + ")");
                }
                case Opcodes.GET_INDEX -> {
                    String value = popExpression(stack);
                    stack.add("state.getIndex(" + value + ", " + operands[0] + ")");
                }
                case Opcodes.GET_PROPERTY_REG ->
                    stack.add(propertyExpression(operands[0], operands[1]));
                case Opcodes.GET_INDEX_REG ->
                    stack.add("state.getIndex(" + registerExpression(operands[0])
                            + ", " + operands[1] + ")");
                case Opcodes.IS_TRUE -> {
                    String value = popExpression(stack);
                    stack.add("Boolean.valueOf(" + value + " == Boolean.TRUE)");
                }
                case Opcodes.TEST_REGISTER_IS_TRUE ->
                    stack.add("Boolean.valueOf(" + registerExpression(operands[0]) + " == Boolean.TRUE)");
                case Opcodes.TEST_REGISTER_IS_FALSE ->
                    stack.add("Boolean.valueOf(" + registerExpression(operands[0]) + " == Boolean.FALSE)");
                case Opcodes.EQUALS -> {
                    List<String> values = popExpressions(stack, 2);
                    stack.add("Boolean.valueOf(java.util.Objects.equals("
                            + values.get(0) + ", " + values.get(1) + "))");
                }
                case Opcodes.STRING_EQUALS, Opcodes.BOOLEAN_EQUALS -> {
                    List<String> values = popExpressions(stack, 2);
                    stack.add("Boolean.valueOf(equalsNonNull("
                            + values.get(0) + ", " + values.get(1) + "))");
                }
                case Opcodes.SUBSTRING -> {
                    String value = popExpression(stack);
                    stack.add("state.substring(" + asString(value) + ", "
                            + operands[0] + ", " + operands[1] + ", " + (operands[2] != 0) + ")");
                }
                case Opcodes.IS_VALID_HOST_LABEL -> {
                    List<String> values = popExpressions(stack, 2);
                    stack.add("Boolean.valueOf(state.isValidHostLabel(" + asString(values.get(0))
                            + ", Boolean.TRUE.equals(" + values.get(1) + ")))");
                }
                case Opcodes.PARSE_URL -> {
                    String value = popExpression(stack);
                    stack.add("parseUri(state, " + value + ")");
                }
                case Opcodes.URI_ENCODE -> {
                    String value = popExpression(stack);
                    stack.add("state.uriEncode(" + asString(value) + ")");
                }
                case Opcodes.RETURN_ERROR -> {
                    String value = popExpression(stack);
                    line("        throw new software.amazon.smithy.java.rulesengine.RulesEvaluationError("
                            + asString(value) + ", " + instruction.next() + ");");
                }
                case Opcodes.RETURN_ENDPOINT -> writeExpressionEndpoint(instruction, stack);
                case Opcodes.RETURN_VALUE -> line("        return " + popExpression(stack) + ";");
                case Opcodes.SPLIT -> {
                    List<String> values = popExpressions(stack, 3);
                    stack.add("state.split(" + asString(values.get(0)) + ", " + asString(values.get(1))
                            + ", ((Number) (" + values.get(2) + ")).intValue())");
                }
                case Opcodes.GET_NEGATIVE_INDEX -> {
                    String value = popExpression(stack);
                    stack.add("state.getNegativeIndex(" + value + ", " + operands[0] + ")");
                }
                case Opcodes.GET_NEGATIVE_INDEX_REG ->
                    stack.add("state.getNegativeIndex(" + registerExpression(operands[0])
                            + ", " + operands[1] + ")");
                case Opcodes.SUBSTRING_EQ ->
                    stack.add("Boolean.valueOf(" + substringEqualsExpression(
                            operands[0], operands[1], operands[2], (operands[3] & 1) != 0, operands[4]) + ")");
                case Opcodes.SPLIT_GET ->
                    stack.add("state.splitGet((String) " + registerExpression(operands[0])
                            + ", C" + operands[1] + ", " + (byte) operands[2] + ")");
                case Opcodes.SELECT_BOOL_REG -> {
                    String value = registerExpression(operands[0]);
                    stack.add(value + " != null && " + value
                            + " != Boolean.FALSE ? C" + operands[1] + " : C" + operands[2]);
                }
                case Opcodes.STRING_EQUALS_REG_CONST -> {
                    String value = registerExpression(operands[0]);
                    stack.add("Boolean.valueOf(" + value + " != null && " + value
                            + ".equals(C" + operands[1] + "))");
                }
                case Opcodes.BUILD_URI -> {
                    List<String> values = popExpressions(stack, 2);
                    stack.add("state.buildUri(C" + operands[0] + ", "
                            + asString(values.get(0)) + ", " + asString(values.get(1)) + ")");
                }
                default -> throw new IllegalStateException("Unsupported expression opcode " + opcode);
            }
        }
        return true;
    }

    private static boolean supportsExpression(int opcode) {
        return switch (opcode) {
            case Opcodes.LOAD_CONST, Opcodes.LOAD_CONST_W, Opcodes.LOAD_REGISTER,
                    Opcodes.NOT, Opcodes.ISSET, Opcodes.TEST_REGISTER_ISSET,
                    Opcodes.TEST_REGISTER_NOT_SET, Opcodes.LIST0, Opcodes.LIST1,
                    Opcodes.LIST2, Opcodes.LISTN, Opcodes.MAP0, Opcodes.MAP1,
                    Opcodes.MAP2, Opcodes.MAP3, Opcodes.MAP4, Opcodes.MAPN,
                    Opcodes.STRUCTN, Opcodes.RESOLVE_TEMPLATE, Opcodes.FN0,
                    Opcodes.FN1, Opcodes.FN2, Opcodes.FN3, Opcodes.FN,
                    Opcodes.GET_PROPERTY, Opcodes.GET_INDEX, Opcodes.GET_PROPERTY_REG,
                    Opcodes.GET_INDEX_REG, Opcodes.IS_TRUE, Opcodes.TEST_REGISTER_IS_TRUE,
                    Opcodes.TEST_REGISTER_IS_FALSE, Opcodes.EQUALS, Opcodes.STRING_EQUALS,
                    Opcodes.BOOLEAN_EQUALS, Opcodes.SUBSTRING, Opcodes.IS_VALID_HOST_LABEL,
                    Opcodes.PARSE_URL, Opcodes.URI_ENCODE, Opcodes.RETURN_ERROR,
                    Opcodes.RETURN_ENDPOINT, Opcodes.RETURN_VALUE, Opcodes.SPLIT,
                    Opcodes.GET_NEGATIVE_INDEX, Opcodes.GET_NEGATIVE_INDEX_REG,
                    Opcodes.SUBSTRING_EQ, Opcodes.SPLIT_GET, Opcodes.SELECT_BOOL_REG,
                    Opcodes.STRING_EQUALS_REG_CONST, Opcodes.BUILD_URI ->
                true;
            default -> false;
        };
    }

    private static String popExpression(List<String> stack) {
        return stack.removeLast();
    }

    private static List<String> popExpressions(List<String> stack, int count) {
        int start = stack.size() - count;
        List<String> result = new ArrayList<>(stack.subList(start, stack.size()));
        stack.subList(start, stack.size()).clear();
        return result;
    }

    private static String asString(String expression) {
        return "(String) (" + expression + ")";
    }

    private String propertyExpression(int register, int property) {
        String target = registerExpression(register);
        return structuredRegisters.contains(register)
                ? "structuredProperty(" + target + ", C" + property + ")"
                : "state.getProperty(" + target + ", C" + property + ")";
    }

    private String registerExpression(int register) {
        return bytecode.getRegisterDefinitions()[register].builtin() == null
                ? "state.r" + register
                : "r" + register + "(state)";
    }

    private String substringEqualsExpression(int register, int start, int end, boolean reverse, int expected) {
        Object value = bytecode.getConstantPool()[expected];
        String input = registerExpression(register);
        if (start == 0 && value instanceof String string && string.length() == end) {
            return input + " != null && ((String) " + input + ")."
                    + (reverse ? "endsWith" : "startsWith") + "(C" + expected + ")";
        }
        return "state.substringEquals((String) " + input
                + ", " + start + ", " + end + ", " + reverse + ", C" + expected + ")";
    }

    private boolean isStructuredFunction(int function) {
        String name = bytecode.getFunctions()[function].getFunctionName();
        return "aws.partition".equals(name) || "aws.parseArn".equals(name);
    }

    private void writeExpressionEndpoint(Instruction instruction, List<String> stack) {
        int flags = instruction.operands[0];
        boolean hasHeaders = (flags & 1) != 0;
        boolean hasProperties = (flags & 2) != 0;
        String url = popExpression(stack);
        String properties = hasProperties
                ? "(java.util.Map<String, Object>) (java.util.Map) (" + popExpression(stack) + ")"
                : "java.util.Map.of()";
        String headers = hasHeaders
                ? "(java.util.Map<String, java.util.List<String>>) (java.util.Map) ("
                        + popExpression(stack) + ")"
                : "java.util.Map.of()";
        if (hasHeaders && hasProperties) {
            line("        return endpoint(state, " + headers + ", " + properties + ", " + url + ");");
        } else if (hasHeaders) {
            line("        return endpointWithHeaders(state, " + headers + ", " + url + ");");
        } else if (hasProperties) {
            line("        return endpointWithProperties(state, " + properties + ", " + url + ");");
        } else {
            line("        return state.endpoint(" + url + ", java.util.Map.of(), java.util.Map.of());");
        }
    }

    private void writeStraightLineProgram(Program program) {
        List<Instruction> ordered = new ArrayList<>(program.instructions.values());
        for (int i = 0; i < ordered.size(); i++) {
            Instruction instruction = ordered.get(i);
            AuthSchemeProperties authScheme = authSchemeProperties(program, instruction);
            if (authScheme != null) {
                int authBase = program.depths.get(instruction.pc) - 8;
                int resultBase = authBase - authScheme.extraEntries * 2;
                StringBuilder call = new StringBuilder("        s")
                        .append(resultBase)
                        .append(" = state.authSchemeProperties(");
                if (authScheme.extraEntries == 1) {
                    call.append("s")
                            .append(resultBase)
                            .append(", (String) s")
                            .append(resultBase + 1)
                            .append(", ");
                }
                call.append("s")
                        .append(authBase)
                        .append(", (String) s")
                        .append(authBase + 1)
                        .append(", s")
                        .append(authBase + 2)
                        .append(", (String) s")
                        .append(authBase + 3)
                        .append(", s")
                        .append(authBase + 4)
                        .append(", (String) s")
                        .append(authBase + 5)
                        .append(", s")
                        .append(authBase + 6)
                        .append(", (String) s")
                        .append(authBase + 7)
                        .append(");");
                line(call.toString());
                i += 3;
                continue;
            }
            UriTemplate template = uriTemplate(program, instruction);
            if (template == null) {
                writeInstruction(instruction, program.depths.get(instruction.pc), "        ");
                continue;
            }

            int count = instruction.operands[0];
            int base = program.depths.get(instruction.pc) - count;
            StringBuilder call = new StringBuilder("        s")
                    .append(base)
                    .append(" = buildUri")
                    .append(count)
                    .append("(C")
                    .append(template.buildUri.operands[0])
                    .append(", (String) C")
                    .append(template.path.operands[0]);
            for (int part = 0; part < count; part++) {
                call.append(", (String) s").append(base + part);
            }
            call.append(");");
            line(call.toString());
            i += 2;
        }
    }

    private AuthSchemeProperties authSchemeProperties(Program program, Instruction instruction) {
        if (instruction.opcode != Opcodes.STRUCTN || instruction.operands[0] != 4) {
            return null;
        }
        Instruction list = program.instructions.get(instruction.next());
        Instruction key = list == null ? null : program.instructions.get(list.next());
        Instruction map = key == null ? null : program.instructions.get(key.next());
        if (list != null
                && list.opcode == Opcodes.LIST1
                && key != null
                && (key.opcode == Opcodes.LOAD_CONST || key.opcode == Opcodes.LOAD_CONST_W)
                && "authSchemes".equals(bytecode.getConstantPool()[key.operands[0]])
                && map != null
                && (map.opcode == Opcodes.MAP1 || map.opcode == Opcodes.MAP2)) {
            int extraEntries = map.opcode == Opcodes.MAP2 ? 1 : 0;
            int authBase = program.depths.get(instruction.pc) - 8;
            return authBase == extraEntries * 2 ? new AuthSchemeProperties(extraEntries) : null;
        }
        return null;
    }

    private void analyzePrograms() {
        for (int i = 0; i < bytecode.getConditionCount(); i++) {
            analyzeProgram(readProgram(bytecode.getConditionStartOffset(i)));
        }
        for (int i = 0; i < bytecode.getResultCount(); i++) {
            analyzeProgram(readProgram(bytecode.getResultOffset(i)));
        }
        analyzeSubstringBindings();
    }

    private void analyzeSubstringBindings() {
        for (int condition = 0; condition < bytecode.getConditionCount(); condition++) {
            Program program = readProgram(bytecode.getConditionStartOffset(condition));
            List<Instruction> body = new ArrayList<>(program.instructions.values());
            if (program.hasControlFlow
                    || body.size() != 3
                    || body.get(0).opcode != Opcodes.LOAD_REGISTER
                    || body.get(1).opcode != Opcodes.SUBSTRING
                    || body.get(2).opcode != Opcodes.SET_REG_RETURN) {
                continue;
            }

            int targetRegister = body.get(2).operands[0];
            int equalityCondition = -1;
            int expectedConstant = -1;
            for (int candidate = 0; candidate < bytecode.getConditionCount(); candidate++) {
                if (bytecode.conditionTypes[candidate] == Bytecode.COND_STRING_EQ_REG_CONST) {
                    int packed = bytecode.conditionOperands[candidate];
                    if ((packed & 0xff) == targetRegister) {
                        if (equalityCondition >= 0) {
                            equalityCondition = -1;
                            break;
                        }
                        equalityCondition = candidate;
                        expectedConstant = packed >>> 8;
                    }
                }
            }
            if (equalityCondition < 0
                    || hasOtherRegisterReader(targetRegister, condition, equalityCondition)
                    || !allSubstringNodesCollapse(condition, equalityCondition)) {
                continue;
            }

            int[] substring = body.get(1).operands;
            substringBindings.put(condition,
                    new SubstringBinding(
                            body.get(0).operands[0],
                            targetRegister,
                            substring[0],
                            substring[1],
                            substring[2] != 0,
                            expectedConstant));
        }
    }

    private boolean hasOtherRegisterReader(int register, int bindingCondition, int equalityCondition) {
        for (int condition = 0; condition < bytecode.getConditionCount(); condition++) {
            if (condition == bindingCondition || condition == equalityCondition) {
                continue;
            }
            if (readsRegister(readProgram(bytecode.getConditionStartOffset(condition)), register)) {
                return true;
            }
        }
        for (int result = 0; result < bytecode.getResultCount(); result++) {
            if (readsRegister(readProgram(bytecode.getResultOffset(result)), register)) {
                return true;
            }
        }
        return false;
    }

    private static boolean readsRegister(Program program, int register) {
        for (Instruction instruction : program.instructions.values()) {
            int opcode = instruction.opcode & 0xff;
            if (switch (opcode) {
                case Opcodes.LOAD_REGISTER,
                        Opcodes.TEST_REGISTER_ISSET,
                        Opcodes.TEST_REGISTER_NOT_SET,
                        Opcodes.GET_PROPERTY_REG,
                        Opcodes.GET_INDEX_REG,
                        Opcodes.TEST_REGISTER_IS_TRUE,
                        Opcodes.TEST_REGISTER_IS_FALSE,
                        Opcodes.GET_NEGATIVE_INDEX_REG,
                        Opcodes.SUBSTRING_EQ,
                        Opcodes.SPLIT_GET,
                        Opcodes.SELECT_BOOL_REG,
                        Opcodes.STRING_EQUALS_REG_CONST ->
                    instruction.operands[0] == register;
                default -> false;
            }) {
                return true;
            }
        }
        return false;
    }

    private boolean allSubstringNodesCollapse(int condition, int equalityCondition) {
        int[] nodes = bytecode.getBddNodes();
        boolean found = false;
        for (int index = 1; index < bytecode.getBddNodeCount(); index++) {
            int base = index * 3;
            if (nodes[base] != condition) {
                continue;
            }
            found = true;
            int high = nodes[base + 1];
            if (high <= 1 || high >= Bdd.RESULT_OFFSET) {
                return false;
            }
            int equalityBase = (high - 1) * 3;
            if (nodes[equalityBase] != equalityCondition
                    || nodes[equalityBase + 2] != nodes[base + 2]) {
                return false;
            }
        }
        return found;
    }

    private void analyzeProgram(Program program) {
        if (program.hasControlFlow) {
            return;
        }
        for (Instruction instruction : program.instructions.values()) {
            if (instruction.opcode == Opcodes.FN1
                    && isStructuredFunction(instruction.operands[0])) {
                Instruction next = program.instructions.get(instruction.next());
                if (next != null
                        && (next.opcode == Opcodes.SET_REGISTER || next.opcode == Opcodes.SET_REG_RETURN)) {
                    structuredRegisters.add(next.operands[0]);
                }
            }
            UriTemplate template = uriTemplate(program, instruction);
            if (template != null) {
                uriTemplateSizes.add(instruction.operands[0]);
            }
        }
    }

    private UriTemplate uriTemplate(Program program, Instruction instruction) {
        if (instruction.opcode != Opcodes.RESOLVE_TEMPLATE || instruction.operands[0] > 8) {
            return null;
        }
        Instruction path = program.instructions.get(instruction.next());
        Instruction buildUri = path == null ? null : program.instructions.get(path.next());
        if (path == null
                || (path.opcode != Opcodes.LOAD_CONST && path.opcode != Opcodes.LOAD_CONST_W)
                || !(bytecode.getConstantPool()[path.operands[0]] instanceof String)
                || buildUri == null
                || buildUri.opcode != Opcodes.BUILD_URI) {
            return null;
        }
        return new UriTemplate(path, buildUri);
    }

    private BooleanSelection coalescedBooleanSelection(Program program) {
        if (!program.hasControlFlow || program.instructions.size() != 8) {
            return null;
        }
        List<Instruction> body = new ArrayList<>(program.instructions.values());
        if (body.get(0).opcode != Opcodes.LOAD_REGISTER
                || body.get(1).opcode != Opcodes.JNN_OR_POP
                || (body.get(2).opcode != Opcodes.LOAD_CONST && body.get(2).opcode != Opcodes.LOAD_CONST_W)
                || body.get(3).opcode != Opcodes.JMP_IF_FALSE
                || (body.get(4).opcode != Opcodes.LOAD_CONST && body.get(4).opcode != Opcodes.LOAD_CONST_W)
                || body.get(5).opcode != Opcodes.JUMP
                || (body.get(6).opcode != Opcodes.LOAD_CONST && body.get(6).opcode != Opcodes.LOAD_CONST_W)
                || body.get(7).opcode != Opcodes.SET_REG_RETURN
                || body.get(1).target != body.get(3).pc
                || body.get(5).target != body.get(7).pc
                || body.get(3).target != body.get(6).pc) {
            return null;
        }
        Object defaultValue = bytecode.getConstantPool()[body.get(2).operands[0]];
        if (!(defaultValue instanceof Boolean bool)) {
            return null;
        }
        return new BooleanSelection(
                body.get(0).operands[0],
                body.get(7).operands[0],
                bool,
                body.get(4).operands[0],
                body.get(6).operands[0]);
    }

    private String optimizedProgramExpression(Program program, int start) {
        Instruction load = program.instructions.get(start);
        if (load == null || load.opcode != Opcodes.LOAD_REGISTER) {
            return null;
        }
        Instruction substring = program.instructions.get(load.next());
        if (substring == null || substring.opcode != Opcodes.SUBSTRING) {
            return null;
        }
        Instruction branch = program.instructions.get(substring.next());
        if (branch == null || branch.opcode != Opcodes.JNN_OR_POP) {
            return null;
        }
        Instruction fallback = program.instructions.get(branch.next());
        Instruction expected = program.instructions.get(branch.target);
        if (fallback == null
                || (fallback.opcode != Opcodes.LOAD_CONST && fallback.opcode != Opcodes.LOAD_CONST_W)
                || expected == null
                || (expected.opcode != Opcodes.LOAD_CONST && expected.opcode != Opcodes.LOAD_CONST_W)
                || fallback.next() != branch.target
                || !"".equals(bytecode.getConstantPool()[fallback.operands[0]])) {
            return null;
        }
        Object expectedValue = bytecode.getConstantPool()[expected.operands[0]];
        if (!(expectedValue instanceof String expectedString) || expectedString.isEmpty()) {
            return null;
        }
        Instruction equals = program.instructions.get(expected.next());
        Instruction result = equals == null ? null : program.instructions.get(equals.next());
        if (equals == null
                || equals.opcode != Opcodes.STRING_EQUALS
                || result == null
                || result.opcode != Opcodes.RETURN_VALUE
                || program.instructions.size() != 7) {
            return null;
        }
        return substringEqualsExpression(
                load.operands[0],
                substring.operands[0],
                substring.operands[1],
                substring.operands[2] != 0,
                expected.operands[0]);
    }

    private Program readProgram(int start) {
        Map<Integer, Instruction> found = new TreeMap<>();
        Set<Integer> leaders = new HashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        leaders.add(start);
        pending.add(start);
        boolean hasControlFlow = false;

        while (!pending.isEmpty()) {
            int pc = pending.removeFirst();
            while (!found.containsKey(pc)) {
                Instruction instruction = readInstruction(pc);
                found.put(pc, instruction);
                if (instruction.isReturn()) {
                    break;
                }
                if (instruction.isControlFlow()) {
                    hasControlFlow = true;
                    leaders.add(instruction.target);
                    pending.add(instruction.target);
                    if (instruction.opcode != Opcodes.JUMP) {
                        leaders.add(instruction.next());
                        pending.add(instruction.next());
                    }
                    break;
                }
                pc = instruction.next();
            }
        }

        Map<Integer, Integer> depths = computeDepths(start, found);
        int maxStack = 0;
        for (Instruction instruction : found.values()) {
            int depth = depths.get(instruction.pc);
            maxStack = Math.max(maxStack, depth + Math.max(0, stackDelta(instruction)));
        }
        List<Integer> sortedLeaders = new ArrayList<>(leaders);
        sortedLeaders.sort(Integer::compareTo);
        return new Program(found, sortedLeaders, hasControlFlow, depths, maxStack);
    }

    private Map<Integer, Integer> computeDepths(int start, Map<Integer, Instruction> instructions) {
        Map<Integer, Integer> depths = new HashMap<>();
        ArrayDeque<Depth> pending = new ArrayDeque<>();
        pending.add(new Depth(start, 0));
        while (!pending.isEmpty()) {
            Depth item = pending.removeFirst();
            Integer previous = depths.putIfAbsent(item.pc, item.depth);
            if (previous != null) {
                if (previous != item.depth) {
                    throw new IllegalArgumentException("Inconsistent stack depth at " + item.pc);
                }
                continue;
            }
            Instruction instruction = instructions.get(item.pc);
            if (instruction == null) {
                throw new IllegalArgumentException("Missing instruction at " + item.pc);
            }
            requireDepth(instruction, item.depth);
            if (instruction.isReturn()) {
                continue;
            }
            if (instruction.opcode == Opcodes.JNN_OR_POP) {
                pending.add(new Depth(instruction.target, item.depth));
                pending.add(new Depth(instruction.next(), item.depth - 1));
            } else if (instruction.opcode == Opcodes.JMP_IF_FALSE) {
                pending.add(new Depth(instruction.target, item.depth - 1));
                pending.add(new Depth(instruction.next(), item.depth - 1));
            } else if (instruction.opcode == Opcodes.JUMP) {
                pending.add(new Depth(instruction.target, item.depth));
            } else {
                pending.add(new Depth(instruction.next(), item.depth + stackDelta(instruction)));
            }
        }
        return depths;
    }

    private Instruction readInstruction(int pc) {
        if (pc < 0 || pc >= instructions.length) {
            throw new IllegalArgumentException("Instruction offset out of bounds: " + pc);
        }
        BytecodeWalker walker = new BytecodeWalker(instructions, pc);
        int length = walker.getInstructionLength();
        if (length < 0) {
            throw new IllegalArgumentException("Unknown or truncated opcode at " + pc);
        }
        int[] operands = new int[walker.getOperandCount()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = walker.getOperand(i);
        }
        int target = isControlFlow(walker.currentOpcode()) ? walker.getJumpTarget() : -1;
        return new Instruction(pc, walker.currentOpcode(), operands, length, target);
    }

    private void writeInstruction(Instruction instruction, int depth, String indent) {
        int op = instruction.opcode & 0xff;
        int[] o = instruction.operands;
        int top = depth - 1;
        int suffix = instruction.pc;
        switch (op) {
            case Opcodes.LOAD_CONST, Opcodes.LOAD_CONST_W -> line(indent + "s" + depth + " = C" + o[0] + ";");
            case Opcodes.SET_REGISTER -> line(indent + "state.r" + o[0] + " = s" + top + ";");
            case Opcodes.LOAD_REGISTER ->
                line(indent + "s" + depth + " = " + registerExpression(o[0]) + ";");
            case Opcodes.NOT -> line(indent + "s" + top + " = s" + top
                    + " == Boolean.FALSE ? Boolean.TRUE : Boolean.FALSE;");
            case Opcodes.ISSET -> line(indent + "s" + top + " = s" + top + " != null;");
            case Opcodes.TEST_REGISTER_ISSET ->
                line(indent + "s" + depth + " = " + registerExpression(o[0]) + " != null;");
            case Opcodes.TEST_REGISTER_NOT_SET ->
                line(indent + "s" + depth + " = " + registerExpression(o[0]) + " == null;");
            case Opcodes.LIST0 -> line(indent + "s" + depth + " = java.util.List.of();");
            case Opcodes.LIST1 -> line(indent + "s" + top + " = java.util.List.of(s" + top + ");");
            case Opcodes.LIST2 -> line(indent + "s" + (depth - 2) + " = java.util.List.of(s"
                    + (depth - 2) + ", s" + top + ");");
            case Opcodes.LISTN -> writeList(indent, depth - o[0], o[0], suffix);
            case Opcodes.MAP0 -> line(indent + "s" + depth + " = java.util.Map.of();");
            case Opcodes.MAP1, Opcodes.MAP2, Opcodes.MAP3, Opcodes.MAP4, Opcodes.MAPN ->
                writeMap(indent, depth - mapSize(instruction) * 2, mapSize(instruction), suffix);
            case Opcodes.STRUCTN -> {
                int count = o[0];
                int base = depth - count * 2;
                structureSizes.add(count);
                StringBuilder args = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        args.append(", ");
                    }
                    args.append("s").append(base + i * 2).append(", (String) s").append(base + i * 2 + 1);
                }
                line(indent + "s" + base + " = new Struct" + count + "(" + args + ");");
            }
            case Opcodes.RESOLVE_TEMPLATE -> {
                int base = depth - o[0];
                StringBuilder expression = new StringBuilder();
                for (int i = 0; i < o[0]; i++) {
                    if (i > 0) {
                        expression.append(" + ");
                    }
                    expression.append("(String) s").append(base + i);
                }
                line(indent + "s" + base + " = " + expression + ";");
            }
            case Opcodes.FN0 -> line(indent + "s" + depth + " = f" + o[0] + ".apply0();");
            case Opcodes.FN1 -> line(indent + "s" + top + " = f" + o[0] + ".apply1(s" + top + ");");
            case Opcodes.FN2 -> {
                int base = depth - 2;
                if ("aws.isVirtualHostableS3Bucket".equals(
                        bytecode.getFunctions()[o[0]].getFunctionName())) {
                    line(indent + "s" + base + " = isVirtualHostableBucket("
                            + "(String) s" + base + ", s" + top + " == Boolean.TRUE);");
                } else {
                    line(indent + "s" + base + " = f" + o[0] + ".apply2(s"
                            + base + ", s" + top + ");");
                }
            }
            case Opcodes.FN3 -> line(indent + "s" + (depth - 3) + " = f" + o[0] + ".apply(s"
                    + (depth - 3) + ", s" + (depth - 2) + ", s" + top + ");");
            case Opcodes.FN -> {
                int count = bytecode.getFunctions()[o[0]].getArgumentCount();
                int base = depth - count;
                StringBuilder args = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        args.append(", ");
                    }
                    args.append("s").append(base + i);
                }
                line(indent + "s" + base + " = f" + o[0] + ".apply(" + args + ");");
            }
            case Opcodes.GET_PROPERTY -> line(indent + "s" + top + " = state.getProperty(s"
                    + top + ", C" + o[0] + ");");
            case Opcodes.GET_INDEX -> line(indent + "s" + top + " = state.getIndex(s" + top + ", " + o[0] + ");");
            case Opcodes.GET_PROPERTY_REG ->
                line(indent + "s" + depth + " = " + propertyExpression(o[0], o[1]) + ";");
            case Opcodes.GET_INDEX_REG -> line(indent + "s" + depth + " = state.getIndex("
                    + registerExpression(o[0]) + ", " + o[1] + ");");
            case Opcodes.IS_TRUE -> line(indent + "s" + top + " = s" + top + " == Boolean.TRUE;");
            case Opcodes.TEST_REGISTER_IS_TRUE ->
                line(indent + "s" + depth + " = " + registerExpression(o[0]) + " == Boolean.TRUE;");
            case Opcodes.TEST_REGISTER_IS_FALSE ->
                line(indent + "s" + depth + " = " + registerExpression(o[0]) + " == Boolean.FALSE;");
            case Opcodes.EQUALS -> line(indent + "s" + (depth - 2) + " = java.util.Objects.equals(s"
                    + (depth - 2) + ", s" + top + ");");
            case Opcodes.STRING_EQUALS, Opcodes.BOOLEAN_EQUALS -> line(indent + "s" + (depth - 2) + " = s"
                    + (depth - 2) + " != null && s" + (depth - 2) + ".equals(s" + top + ");");
            case Opcodes.SUBSTRING -> line(indent + "s" + top + " = state.substring((String) s" + top + ", "
                    + o[0] + ", " + o[1] + ", " + (o[2] != 0) + ");");
            case Opcodes.IS_VALID_HOST_LABEL -> line(indent + "s" + (depth - 2)
                    + " = state.isValidHostLabel((String) s" + (depth - 2)
                    + ", Boolean.TRUE.equals(s" + top + "));");
            case Opcodes.PARSE_URL -> line(indent + "s" + top + " = s" + top
                    + " == null ? null : state.parseUri((String) s" + top + ");");
            case Opcodes.URI_ENCODE -> line(indent + "s" + top + " = state.uriEncode((String) s" + top + ");");
            case Opcodes.RETURN_ERROR -> line(indent
                    + "throw new software.amazon.smithy.java.rulesengine.RulesEvaluationError((String) s"
                    + top + ", " + instruction.next() + ");");
            case Opcodes.RETURN_ENDPOINT -> writeReturnEndpoint(instruction, depth, indent);
            case Opcodes.RETURN_VALUE -> line(indent + "return s" + top + ";");
            case Opcodes.JNN_OR_POP -> {
                line(indent + "pc = s" + top + " != null ? " + instruction.target + " : " + instruction.next() + ";");
                line(indent + "continue;");
            }
            case Opcodes.SPLIT -> line(indent + "s" + (depth - 3) + " = state.split((String) s"
                    + (depth - 3) + ", (String) s" + (depth - 2) + ", ((Number) s" + top + ").intValue());");
            case Opcodes.GET_NEGATIVE_INDEX -> line(indent + "s" + top
                    + " = state.getNegativeIndex(s" + top + ", " + o[0] + ");");
            case Opcodes.GET_NEGATIVE_INDEX_REG -> line(indent + "s" + depth
                    + " = state.getNegativeIndex(" + registerExpression(o[0]) + ", " + o[1] + ");");
            case Opcodes.JMP_IF_FALSE -> {
                line(indent + "pc = s" + top + " != Boolean.TRUE ? " + instruction.target
                        + " : " + instruction.next() + ";");
                line(indent + "continue;");
            }
            case Opcodes.JUMP -> {
                line(indent + "pc = " + instruction.target + ";");
                line(indent + "continue;");
            }
            case Opcodes.SUBSTRING_EQ -> line(indent + "s" + depth + " = "
                    + substringEqualsExpression(o[0], o[1], o[2], (o[3] & 1) != 0, o[4]) + ";");
            case Opcodes.SPLIT_GET -> line(indent + "s" + depth + " = state.splitGet((String) "
                    + registerExpression(o[0]) + ", C" + o[1] + ", " + (byte) o[2] + ");");
            case Opcodes.SELECT_BOOL_REG -> {
                String value = registerExpression(o[0]);
                line(indent + "s" + depth + " = " + value
                        + " != null && " + value + " != Boolean.FALSE ? C" + o[1] + " : C" + o[2] + ";");
            }
            case Opcodes.STRING_EQUALS_REG_CONST -> {
                String value = registerExpression(o[0]);
                line(indent + "s" + depth + " = " + value
                        + " != null && " + value + ".equals(C" + o[1] + ");");
            }
            case Opcodes.SET_REG_RETURN -> {
                line(indent + "state.r" + o[0] + " = s" + top + ";");
                line(indent + "return s" + top + ";");
            }
            case Opcodes.BUILD_URI -> line(indent + "s" + (depth - 2) + " = state.buildUri(C" + o[0]
                    + ", (String) s" + (depth - 2) + ", (String) s" + top + ");");
            default -> throw new IllegalArgumentException("Unsupported opcode " + op + " at " + instruction.pc);
        }
    }

    private void writeList(String indent, int base, int count, int suffix) {
        line(indent + "java.util.ArrayList<Object> list" + suffix + " = new java.util.ArrayList<>(" + count + ");");
        for (int i = 0; i < count; i++) {
            line(indent + "list" + suffix + ".add(s" + (base + i) + ");");
        }
        line(indent + "s" + base + " = list" + suffix + ";");
    }

    private void writeMap(String indent, int base, int count, int suffix) {
        if (count == 1) {
            line(indent + "s" + base + " = java.util.Collections.singletonMap("
                    + "(String) s" + (base + 1) + ", s" + base + ");");
            return;
        }
        line(indent + "java.util.Map<String, Object> map" + suffix + " = new java.util.HashMap<>("
                + (count + 1) + ", 1.0f);");
        for (int i = count - 1; i >= 0; i--) {
            line(indent + "map" + suffix + ".put((String) s" + (base + i * 2 + 1)
                    + ", s" + (base + i * 2) + ");");
        }
        line(indent + "s" + base + " = map" + suffix + ";");
    }

    private void writeReturnEndpoint(Instruction instruction, int depth, String indent) {
        int flags = instruction.operands[0];
        boolean hasHeaders = (flags & 1) != 0;
        boolean hasProperties = (flags & 2) != 0;
        int url = depth - 1;
        int properties = url - (hasProperties ? 1 : 0);
        int headers = properties - (hasHeaders ? 1 : 0);
        line(indent + "return state.endpoint(s" + url + ", "
                + (hasProperties ? "(java.util.Map<String, Object>) s" + properties : "java.util.Map.of()")
                + ", " + (hasHeaders
                        ? "(java.util.Map<String, java.util.List<String>>) s" + headers
                        : "java.util.Map.of()")
                + ");");
    }

    private static int mapSize(Instruction instruction) {
        return switch (instruction.opcode) {
            case Opcodes.MAP1 -> 1;
            case Opcodes.MAP2 -> 2;
            case Opcodes.MAP3 -> 3;
            case Opcodes.MAP4 -> 4;
            case Opcodes.MAPN -> instruction.operands[0];
            default -> throw new IllegalArgumentException();
        };
    }

    private int stackDelta(Instruction instruction) {
        int op = instruction.opcode & 0xff;
        return switch (op) {
            case Opcodes.LOAD_CONST, Opcodes.LOAD_CONST_W, Opcodes.LOAD_REGISTER,
                    Opcodes.TEST_REGISTER_ISSET, Opcodes.TEST_REGISTER_NOT_SET, Opcodes.LIST0, Opcodes.MAP0,
                    Opcodes.FN0, Opcodes.GET_PROPERTY_REG, Opcodes.GET_INDEX_REG,
                    Opcodes.TEST_REGISTER_IS_TRUE, Opcodes.TEST_REGISTER_IS_FALSE,
                    Opcodes.GET_NEGATIVE_INDEX_REG, Opcodes.SUBSTRING_EQ, Opcodes.SPLIT_GET,
                    Opcodes.SELECT_BOOL_REG, Opcodes.STRING_EQUALS_REG_CONST ->
                1;
            case Opcodes.SET_REGISTER, Opcodes.NOT, Opcodes.ISSET, Opcodes.LIST1, Opcodes.GET_PROPERTY,
                    Opcodes.GET_INDEX, Opcodes.IS_TRUE, Opcodes.SUBSTRING, Opcodes.PARSE_URL, Opcodes.URI_ENCODE,
                    Opcodes.GET_NEGATIVE_INDEX, Opcodes.FN1 ->
                0;
            case Opcodes.LIST2, Opcodes.MAP1, Opcodes.EQUALS, Opcodes.STRING_EQUALS, Opcodes.BOOLEAN_EQUALS,
                    Opcodes.IS_VALID_HOST_LABEL, Opcodes.FN2, Opcodes.BUILD_URI ->
                -1;
            case Opcodes.SPLIT, Opcodes.FN3 -> -2;
            case Opcodes.LISTN, Opcodes.RESOLVE_TEMPLATE -> 1 - instruction.operands[0];
            case Opcodes.MAP2, Opcodes.MAP3, Opcodes.MAP4, Opcodes.MAPN ->
                1 - mapSize(instruction) * 2;
            case Opcodes.STRUCTN -> 1 - instruction.operands[0] * 2;
            case Opcodes.FN -> 1 - bytecode.getFunctions()[instruction.operands[0]].getArgumentCount();
            case Opcodes.JNN_OR_POP, Opcodes.JMP_IF_FALSE, Opcodes.JUMP,
                    Opcodes.RETURN_ERROR, Opcodes.RETURN_ENDPOINT, Opcodes.RETURN_VALUE,
                    Opcodes.SET_REG_RETURN ->
                0;
            default -> throw new IllegalArgumentException("Unsupported opcode " + op);
        };
    }

    private void requireDepth(Instruction instruction, int depth) {
        int required = switch (instruction.opcode) {
            case Opcodes.SET_REGISTER, Opcodes.NOT, Opcodes.ISSET, Opcodes.LIST1, Opcodes.FN1,
                    Opcodes.GET_PROPERTY, Opcodes.GET_INDEX, Opcodes.IS_TRUE, Opcodes.SUBSTRING,
                    Opcodes.PARSE_URL, Opcodes.URI_ENCODE, Opcodes.RETURN_ERROR, Opcodes.RETURN_VALUE,
                    Opcodes.JNN_OR_POP, Opcodes.GET_NEGATIVE_INDEX, Opcodes.SET_REG_RETURN ->
                1;
            case Opcodes.LIST2, Opcodes.MAP1, Opcodes.FN2, Opcodes.EQUALS, Opcodes.STRING_EQUALS,
                    Opcodes.BOOLEAN_EQUALS, Opcodes.IS_VALID_HOST_LABEL, Opcodes.BUILD_URI ->
                2;
            case Opcodes.FN3, Opcodes.SPLIT -> 3;
            case Opcodes.LISTN, Opcodes.RESOLVE_TEMPLATE -> instruction.operands[0];
            case Opcodes.MAP2, Opcodes.MAP3, Opcodes.MAP4, Opcodes.MAPN -> mapSize(instruction) * 2;
            case Opcodes.STRUCTN -> instruction.operands[0] * 2;
            case Opcodes.FN -> bytecode.getFunctions()[instruction.operands[0]].getArgumentCount();
            case Opcodes.JMP_IF_FALSE -> 1;
            case Opcodes.RETURN_ENDPOINT -> 1 + ((instruction.operands[0] & 1) != 0 ? 1 : 0)
                    + ((instruction.operands[0] & 2) != 0 ? 1 : 0);
            default -> 0;
        };
        if (depth < required) {
            throw new IllegalArgumentException("Stack underflow at " + instruction.pc);
        }
    }

    private void writeHelpers() {
        line("");
        line("    private static boolean truthy(Object value) {");
        line("        return value != null && value != Boolean.FALSE;");
        line("    }");
        line("");
        line("    private static IllegalStateException invalidPc(int pc) {");
        line("        return new IllegalStateException(\"Invalid generated pc: \" + pc);");
        line("    }");
        line("");
        line("    private static boolean equalsNonNull(Object left, Object right) {");
        line("        return left != null && left.equals(right);");
        line("    }");
        if (!structuredRegisters.isEmpty()) {
            line("");
            line("    private static Object structuredProperty(Object value, String property) {");
            line("        return value == null ? null");
            line("                : ((software.amazon.smithy.java.rulesengine.PropertyGetter) value)"
                    + ".getProperty(property);");
            line("    }");
        }
        line("");
        line("    private static software.amazon.smithy.java.io.uri.SmithyUri parseUri("
                + "State state, Object value) {");
        line("        return value == null ? null : state.parseUri((String) value);");
        line("    }");
        line("");
        line("    private static software.amazon.smithy.java.endpoints.Endpoint endpointWithProperties(");
        line("            State state, java.util.Map<String, Object> properties, Object url) {");
        line("        return state.endpoint(url, properties, java.util.Map.of());");
        line("    }");
        line("");
        line("    private static software.amazon.smithy.java.endpoints.Endpoint endpointWithHeaders(");
        line("            State state, java.util.Map<String, java.util.List<String>> headers, Object url) {");
        line("        return state.endpoint(url, java.util.Map.of(), headers);");
        line("    }");
        line("");
        line("    private static software.amazon.smithy.java.endpoints.Endpoint endpoint(");
        line("            State state, java.util.Map<String, java.util.List<String>> headers,");
        line("            java.util.Map<String, Object> properties, Object url) {");
        line("        return state.endpoint(url, properties, headers);");
        line("    }");

        for (int size : sorted(uriTemplateSizes)) {
            line("");
            StringBuilder parameters = new StringBuilder(
                    "String scheme, String path");
            StringBuilder host = new StringBuilder();
            for (int i = 0; i < size; i++) {
                parameters.append(", String p").append(i);
                if (i > 0) {
                    host.append(" + ");
                }
                host.append("p").append(i);
            }
            line("    private static software.amazon.smithy.java.io.uri.SmithyUri buildUri"
                    + size + "(" + parameters + ") {");
            line("        String host = " + host + ";");
            line("        return software.amazon.smithy.java.io.uri.SmithyUri.ofTrusted("
                    + "scheme, host, -1, path, null);");
            line("    }");
        }
        if (hasVirtualHostFunction()) {
            line("");
            line("    private static boolean isVirtualHostableBucket("
                    + "String bucket, boolean allowDots) {");
            line("        return software.amazon.smithy.rulesengine.aws.language.functions");
            line("                .IsVirtualHostableS3Bucket.isVirtualHostableBucket(bucket, allowDots);");
            line("    }");
        }

        for (int size : sorted(listSizes)) {
            line("");
            StringBuilder parameters = new StringBuilder();
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    parameters.append(", ");
                }
                parameters.append("Object v").append(i);
            }
            line("    private static java.util.List<Object> list" + size + "(" + parameters + ") {");
            line("        var result = new java.util.ArrayList<Object>(" + size + ");");
            for (int i = 0; i < size; i++) {
                line("        result.add(v" + i + ");");
            }
            line("        return result;");
            line("    }");
        }

        for (int size : sorted(mapSizes)) {
            line("");
            StringBuilder parameters = new StringBuilder();
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    parameters.append(", ");
                }
                parameters.append("Object v").append(i).append(", String k").append(i);
            }
            line("    private static java.util.Map<String, Object> map" + size + "(" + parameters + ") {");
            if (size == 1) {
                line("        return java.util.Collections.singletonMap(k0, v0);");
            } else {
                line("        var result = new java.util.HashMap<String, Object>(" + (size + 1) + ", 1.0f);");
                for (int i = size - 1; i >= 0; i--) {
                    line("        result.put(k" + i + ", v" + i + ");");
                }
                line("        return result;");
            }
            line("    }");
        }

        for (int size : sorted(structureSizes)) {
            line("");
            StringBuilder components = new StringBuilder();
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    components.append(", ");
                }
                components.append("Object v").append(i).append(", String k").append(i);
            }
            line("    private record Struct" + size + "(" + components + ")");
            line("            implements software.amazon.smithy.java.rulesengine.PropertyGetter {");
            line("        @Override");
            line("        public Object getProperty(String name) {");
            for (int i = 0; i < size; i++) {
                line("            if (name.equals(k" + i + ")) return v" + i + ";");
            }
            line("            return null;");
            line("        }");
            line("    }");
        }
    }

    private static String registerType(RegisterDefinition register) {
        if (register.type() == ParameterType.STRING) {
            return "String";
        } else if (register.type() == ParameterType.BOOLEAN) {
            return "Boolean";
        }
        return "Object";
    }

    private static String castRegisterValue(RegisterDefinition register, String expression) {
        String type = registerType(register);
        return type.equals("Object") ? expression : "(" + type + ") " + expression;
    }

    private static String registerInitialValue(RegisterDefinition register, boolean useDefault) {
        return constantValue(useDefault ? register.defaultValue() : null);
    }

    private static String constantType(Object value) {
        return switch (value) {
            case String ignored -> "String";
            case Integer ignored -> "Integer";
            case Boolean ignored -> "Boolean";
            case List<?> ignored -> "java.util.List<Object>";
            case Map<?, ?> ignored -> "java.util.Map<String, Object>";
            case null -> "Object";
            default -> "Object";
        };
    }

    private boolean hasVirtualHostFunction() {
        for (RulesFunction function : bytecode.getFunctions()) {
            if ("aws.isVirtualHostableS3Bucket".equals(function.getFunctionName())) {
                return true;
            }
        }
        return false;
    }

    private static String constantValue(Object value) {
        return switch (value) {
            case null -> "null";
            case String string -> quote(string);
            case Integer integer -> "Integer.valueOf(" + integer + ")";
            case Boolean bool -> bool ? "Boolean.TRUE" : "Boolean.FALSE";
            case List<?> list -> {
                if (list.isEmpty()) {
                    yield "java.util.List.of()";
                }
                StringBuilder result = new StringBuilder("java.util.List.of(");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        result.append(", ");
                    }
                    result.append(constantValue(list.get(i)));
                }
                yield result.append(')').toString();
            }
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    yield "java.util.Map.of()";
                }
                List<String> entries = new ArrayList<>(map.size());
                for (var entry : map.entrySet()) {
                    entries.add("java.util.Map.entry(" + quote((String) entry.getKey())
                            + ", " + constantValue(entry.getValue()) + ")");
                }
                yield "java.util.Map.ofEntries(" + String.join(", ", entries) + ")";
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported bytecode constant: " + value.getClass().getName());
        };
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static List<Integer> sorted(Set<Integer> values) {
        List<Integer> result = new ArrayList<>(values);
        result.sort(Integer::compareTo);
        return result;
    }

    private static void validateName(String value, boolean simple) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Java name must not be empty");
        }
        String[] components = simple ? new String[] {value} : value.split("\\.");
        for (String component : components) {
            if (component.isEmpty() || !Character.isJavaIdentifierStart(component.charAt(0))) {
                throw new IllegalArgumentException("Invalid Java name: " + value);
            }
            for (int i = 1; i < component.length(); i++) {
                if (!Character.isJavaIdentifierPart(component.charAt(i))) {
                    throw new IllegalArgumentException("Invalid Java name: " + value);
                }
            }
        }
    }

    private static boolean isControlFlow(byte opcode) {
        return opcode == Opcodes.JNN_OR_POP || opcode == Opcodes.JMP_IF_FALSE || opcode == Opcodes.JUMP;
    }

    private void line(String value) {
        source.append(value).append('\n');
    }

    private record Program(
            Map<Integer, Instruction> instructions,
            List<Integer> leaders,
            boolean hasControlFlow,
            Map<Integer, Integer> depths,
            int maxStack) {}

    private record UriTemplate(Instruction path, Instruction buildUri) {}

    private record AuthSchemeProperties(int extraEntries) {}

    private record BooleanSelection(
            int sourceRegister,
            int targetRegister,
            boolean defaultValue,
            int trueConstant,
            int falseConstant) {}

    private record ConditionBinding(int targetRegister, String expression, boolean alwaysTruthy) {}

    private record SubstringBinding(
            int sourceRegister,
            int targetRegister,
            int start,
            int end,
            boolean reverse,
            int expectedConstant) {}

    private record Instruction(int pc, byte opcode, int[] operands, int length, int target) {
        int next() {
            return pc + length;
        }

        boolean isReturn() {
            return opcode == Opcodes.RETURN_VALUE
                    || opcode == Opcodes.RETURN_ENDPOINT
                    || opcode == Opcodes.RETURN_ERROR
                    || opcode == Opcodes.SET_REG_RETURN;
        }

        boolean isControlFlow() {
            return JavaEndpointResolverGenerator.isControlFlow(opcode);
        }
    }

    private record Depth(int pc, int depth) {}
}
