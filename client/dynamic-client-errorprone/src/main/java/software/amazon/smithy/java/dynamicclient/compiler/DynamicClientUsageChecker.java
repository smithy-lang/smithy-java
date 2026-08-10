/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.compiler;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Error Prone check that validates {@code DynamicClient} usage against the Smithy model each client is actually built
 * from — at compile time, with no code generation.
 *
 * <p>The dynamic client stays fully runtime-driven and document-based. This check only reads the source under
 * compilation: it finds {@code DynamicClient.builder()...build()} chains, resolves the model source by statically
 * walking the {@code Model.assembler()...} chain feeding {@code .model(...)}, assembles that model in-process with the
 * real {@code smithy-model} {@link software.amazon.smithy.model.loader.ModelAssembler}, and checks each
 * {@code client.call("Op", Map.of(...))} site:
 *
 * <ul>
 *     <li>the operation name (a String literal or {@code static final String} constant) exists on the resolved
 *         service; and</li>
 *     <li>if the input is a {@code Map.of("k", v, ...)} literal, every key is a member of the operation input.</li>
 * </ul>
 *
 * <h2>The load-bearing rule: abstain, never false-positive</h2>
 *
 * <p>The check only reasons about statically-resolvable values. The instant a value is genuinely dynamic — an
 * operation name from a field or CLI arg, a model path computed at runtime, {@code addImport(someUrl)} — it returns
 * {@link Description#NO_MATCH}. The whole reason {@code DynamicClient} exists is runtime dynamism; a checker that flags
 * valid dynamic code gets turned off. It catches typos and stays out of the way otherwise.
 *
 * <h2>Running only this check</h2>
 *
 * <p>Consumers who do not want Error Prone's other checks can run this one alone:
 * {@code -Xep:DynamicClientUsage:ERROR -XepDisableAllChecks}.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "DynamicClientUsage",
        summary = "Verifies DynamicClient operation names and input keys against the Smithy model the client is "
                + "built from.",
        severity = BugPattern.SeverityLevel.ERROR,
        link = "https://github.com/smithy-lang/smithy-java",
        linkType = BugPattern.LinkType.CUSTOM)
public final class DynamicClientUsageChecker extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final String DYNAMIC_CLIENT = "software.amazon.smithy.java.dynamicclient.DynamicClient";

    /** Matches any {@code call(...)} instance method on DynamicClient (all overloads take the op name first). */
    private static final Matcher<ExpressionTree> CALL =
            MethodMatchers.instanceMethod().onExactClass(DYNAMIC_CLIENT).named("call");

    /**
     * Cache of resolved clients keyed by the builder variable's symbol, so the model for a given client is assembled
     * once per compilation regardless of how many call sites reference it. Keyed by symbol identity via its string
     * form; a fresh resolver-backed model is cheap to look up but not to assemble.
     */
    private final Map<Symbol, ResolvedClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!CALL.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        var args = tree.getArguments();
        if (args.isEmpty()) {
            return Description.NO_MATCH;
        }

        // Resolve which client this call is made on, and the model it was built from.
        ResolvedClient client = resolveClient(tree, state);
        if (client == null || client.model() == null) {
            return Description.NO_MATCH; // unknown client or unresolved model: abstain.
        }

        // Operation name must be a compile-time constant to validate. ASTHelpers.constValue folds literals + constants.
        String operation = ASTHelpers.constValue(args.get(0), String.class);
        if (operation == null) {
            return Description.NO_MATCH; // dynamic operation name: abstain.
        }

        OperationShape opShape = client.operations().get(operation);
        if (opShape == null) {
            return buildUnknownOperation(tree, args.get(0), operation, client, state);
        }

        // If a second argument is a Map.of(...) literal, validate its keys against the input structure members.
        if (args.size() >= 2) {
            Description inputProblem = validateInputKeys(args.get(1), opShape, client);
            if (inputProblem != null) {
                return inputProblem;
            }
        }
        return Description.NO_MATCH;
    }

    private Description buildUnknownOperation(
            MethodInvocationTree tree,
            ExpressionTree opArg,
            String operation,
            ResolvedClient client,
            VisitorState state
    ) {
        var description = buildDescription(opArg)
                .setMessage(String.format(
                        "Operation '%s' not found in service '%s'. Known operations: %s",
                        operation,
                        client.service().getId(),
                        client.sortedOperationNames()));
        // Suggested fix: if there is a single close match by edit distance, offer it. (Auto-fix is EP's payoff.)
        String suggestion = closestMatch(operation, client.operations().keySet());
        if (suggestion != null) {
            description.addFix(SuggestedFix.replace(opArg, '"' + suggestion + '"'));
        }
        return description.build();
    }

    private Description validateInputKeys(ExpressionTree inputArg, OperationShape opShape, ResolvedClient client) {
        List<String> keys = MapLiteral.keysOf(inputArg, e -> ASTHelpers.constValue(e, String.class));
        if (keys == null) {
            return null; // not a resolvable Map.of(...) literal (e.g. a Document or variable): nothing to check.
        }
        var inputId = opShape.getInputShape();
        var inputShape = client.model().getShape(inputId).orElse(null);
        if (!(inputShape instanceof StructureShape structure)) {
            return null;
        }
        Map<String, MemberShape> members = structure.getAllMembers();
        for (String key : keys) {
            if (!members.containsKey(key)) {
                return buildDescription(inputArg)
                        .setMessage(String.format(
                                "'%s' is not a member of input '%s' for operation '%s'. Known members: %s",
                                key,
                                inputId.getName(),
                                opShape.getId().getName(),
                                members.keySet().stream().sorted().toList()))
                        .build();
            }
        }
        return null;
    }

    /** Resolve the receiver of a {@code call(...)} back to the {@code DynamicClient} builder that produced it. */
    private ResolvedClient resolveClient(MethodInvocationTree call, VisitorState state) {
        ExpressionTree receiver = ASTHelpers.getReceiver(call);
        if (receiver == null) {
            return null;
        }
        Symbol receiverSymbol = ASTHelpers.getSymbol(receiver);
        if (receiverSymbol == null) {
            return null;
        }
        ResolvedClient cached = clientCache.get(receiverSymbol);
        if (cached != null) {
            return cached;
        }
        // Find the variable declaration for the receiver and inspect its initializer builder chain.
        VariableTree declaration = findDeclaration(receiverSymbol, state);
        if (declaration == null || declaration.getInitializer() == null) {
            return null;
        }
        if (!isDynamicClientBuild(declaration.getInitializer())) {
            return null;
        }
        ExpressionTree modelArg = findNamedArgument(declaration.getInitializer(), "model");
        ExpressionTree modelExpr = modelArg == null ? null : deref(modelArg, state);
        Model model = modelExpr == null
                ? null
                : new ModelResolver(e -> ASTHelpers.constValue(e, String.class), List.of()).resolve(modelExpr);
        ExpressionTree serviceArg = findNamedArgument(declaration.getInitializer(), "serviceId");
        String serviceId = resolveServiceId(serviceArg);
        ResolvedClient resolved = ResolvedClient.of(model, serviceId);
        clientCache.put(receiverSymbol, resolved);
        return resolved;
    }

    /**
     * Resolve the service ID from a {@code serviceId(...)} argument. Handles both a String constant and the common
     * {@code ShapeId.from("ns#Name")} form. Returns {@code null} when not statically resolvable, in which case
     * {@link ResolvedClient} falls back to single-service auto-detection like the runtime builder does.
     */
    private String resolveServiceId(ExpressionTree serviceArg) {
        if (serviceArg == null) {
            return null;
        }
        String direct = ASTHelpers.constValue(serviceArg, String.class);
        if (direct != null) {
            return direct;
        }
        // ShapeId.from("smithy.example#Sprockets")
        if (serviceArg instanceof MethodInvocationTree mi
                && mi.getMethodSelect() instanceof MemberSelectTree sel
                && sel.getIdentifier().contentEquals("from")
                && sel.getExpression().toString().endsWith("ShapeId")
                && mi.getArguments().size() == 1) {
            return ASTHelpers.constValue(mi.getArguments().get(0), String.class);
        }
        return null;
    }

    /** Whether an initializer is a {@code DynamicClient.builder()...build()} chain. */
    private boolean isDynamicClientBuild(ExpressionTree expr) {
        ExpressionTree cursor = expr;
        while (cursor instanceof MethodInvocationTree mi
                && mi.getMethodSelect() instanceof MemberSelectTree sel) {
            if (sel.getIdentifier().contentEquals("builder")
                    && sel.getExpression().toString().endsWith("DynamicClient")) {
                return true;
            }
            cursor = sel.getExpression();
        }
        return false;
    }

    /** Find the argument passed to {@code .name(x)} anywhere in a builder chain. */
    private ExpressionTree findNamedArgument(ExpressionTree expr, String methodName) {
        ExpressionTree cursor = expr;
        while (cursor instanceof MethodInvocationTree mi
                && mi.getMethodSelect() instanceof MemberSelectTree sel) {
            if (sel.getIdentifier().contentEquals(methodName) && !mi.getArguments().isEmpty()) {
                return mi.getArguments().get(0);
            }
            cursor = sel.getExpression();
        }
        return null;
    }

    /** If the model argument is an identifier, dereference it to the initializer of the variable it names. */
    private ExpressionTree deref(ExpressionTree expr, VisitorState state) {
        if (expr instanceof MethodInvocationTree) {
            return expr; // already a Model.assembler()... chain.
        }
        Symbol symbol = ASTHelpers.getSymbol(expr);
        if (symbol == null) {
            return expr;
        }
        VariableTree declaration = findDeclaration(symbol, state);
        if (declaration != null && declaration.getInitializer() != null) {
            return declaration.getInitializer();
        }
        return expr;
    }

    /** Locate the {@link VariableTree} declaring {@code symbol} within the current compilation unit. */
    private VariableTree findDeclaration(Symbol symbol, VisitorState state) {
        return DeclarationFinder.find(symbol, state);
    }

    /** Return the single closest operation name by Levenshtein distance if it is unambiguously close, else null. */
    private static String closestMatch(String typo, Set<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int secondBest = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = levenshtein(typo, candidate);
            if (distance < bestDistance) {
                secondBest = bestDistance;
                bestDistance = distance;
                best = candidate;
            } else if (distance < secondBest) {
                secondBest = distance;
            }
        }
        // Only suggest when there's a clear, close winner (<= a third of the length, and distinctly closest).
        int threshold = Math.max(1, typo.length() / 3);
        if (best != null && bestDistance <= threshold && bestDistance < secondBest) {
            return best;
        }
        return null;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
