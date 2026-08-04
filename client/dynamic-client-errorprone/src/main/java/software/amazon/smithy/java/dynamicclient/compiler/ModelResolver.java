/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.compiler;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.validation.Severity;

/**
 * Resolves the Smithy {@link Model} that a {@code DynamicClient} is built from by statically reading the
 * {@code Model.assembler()...assemble().unwrap()} chain that feeds {@code builder().model(...)}.
 *
 * <p>This is the mechanism-agnostic core of the check: static source resolution, then compile-time assembly using the
 * exact same {@link ModelAssembler} the runtime uses (no reimplementation, no drift). It has no dependency on javac
 * internals or Error Prone — the caller supplies a {@code constantResolver} that maps an expression to its compile-time
 * String value (backed by {@code ASTHelpers.constValue} under Error Prone), so the same resolver drives an Error Prone
 * {@code BugChecker}, a raw javac plugin, or a standalone parser-based task unchanged.
 *
 * <p>Every resolvable chain is assembled at most once and cached, keyed by the ordered set of sources it declares. If
 * any source-adding call in the chain has an argument the resolver cannot reduce to a compile-time constant, the whole
 * model is treated as unresolvable and {@link #resolve} returns {@code null} — the caller then abstains rather than
 * validating against a partial model.
 */
final class ModelResolver {

    private final Function<ExpressionTree, String> constantResolver;
    private final List<String> importRoots;
    private final Map<String, Model> cache = new LinkedHashMap<>();

    /**
     * @param constantResolver maps an expression to its compile-time String value, or {@code null} if it is not a
     *                         compile-time constant (literals and {@code static final String}s included).
     * @param importRoots directories to resolve relative {@code addImport("path")} arguments against.
     */
    ModelResolver(Function<ExpressionTree, String> constantResolver, List<String> importRoots) {
        this.constantResolver = constantResolver;
        this.importRoots = importRoots;
    }

    /**
     * Attempt to resolve and assemble the model from the expression passed to {@code .model(expr)}.
     *
     * @param modelExpr the argument expression given to {@code .model(...)}, already dereferenced to its defining
     *                  initializer where possible.
     * @return the assembled model, or {@code null} if the chain is not fully statically resolvable.
     */
    Model resolve(ExpressionTree modelExpr) {
        if (!(modelExpr instanceof MethodInvocationTree)) {
            return null; // e.g. a bare identifier we could not dereference, or a method return: abstain.
        }
        // Collect the assembler chain sources from the outermost call (unwrap/assemble) inward to assembler().
        var sources = new AssemblerSources();
        if (!collectChain((MethodInvocationTree) modelExpr, sources)) {
            return null; // encountered a non-resolvable source or an unrecognized chain: abstain.
        }
        if (!sources.sawAssembler) {
            return null; // not actually a Model.assembler() chain.
        }
        String key = sources.cacheKey();
        Model cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Model assembled = assemble(sources);
        if (assembled != null) {
            cache.put(key, assembled);
        }
        return assembled;
    }

    /** Walk a fluent chain of method invocations, gathering statically-known model sources. */
    private boolean collectChain(MethodInvocationTree invocation, AssemblerSources sources) {
        if (!(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
            return false;
        }
        String method = select.getIdentifier().toString();
        ExpressionTree receiver = select.getExpression();

        switch (method) {
            case "unwrap", "assemble", "putProperty" -> {
                // Structural / no-op-for-us calls; recurse into the receiver.
            }
            case "discoverModels" -> sources.discoverModels = true;
            case "addUnparsedModel" -> {
                // addUnparsedModel(String name, String content)
                var args = invocation.getArguments();
                if (args.size() != 2) {
                    return false;
                }
                String name = constantResolver.apply(args.get(0));
                String content = constantResolver.apply(args.get(1));
                if (name == null || content == null) {
                    return false; // dynamic content: abstain.
                }
                sources.unparsed.put(name, content);
            }
            case "addImport" -> {
                // addImport(String path) with a literal path. addImport(URL)/computed paths => abstain.
                var args = invocation.getArguments();
                if (args.size() != 1) {
                    return false;
                }
                String path = constantResolver.apply(args.get(0));
                if (path == null) {
                    return false; // e.g. addImport(resourceUrl): abstain.
                }
                sources.imports.add(path);
            }
            case "assembler" -> {
                // Model.assembler() — the root. Stop here.
                sources.sawAssembler = true;
                return true;
            }
            default -> {
                // Unknown builder method in the chain: be conservative and abstain.
                return false;
            }
        }

        if (receiver instanceof MethodInvocationTree next) {
            return collectChain(next, sources);
        }
        // Reached the head of the chain without seeing assembler() (e.g. Model.assembler where assembler is a field).
        return method.equals("assembler");
    }

    private Model assemble(AssemblerSources sources) {
        try {
            ModelAssembler assembler = Model.assembler()
                    .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true);
            if (sources.discoverModels) {
                assembler.discoverModels();
            }
            for (var entry : sources.unparsed.entrySet()) {
                assembler.addUnparsedModel(entry.getKey(), entry.getValue());
            }
            for (String imp : sources.imports) {
                Path resolved = resolveImportPath(imp);
                if (resolved == null) {
                    // Could not locate a literal import against any known root: abstain rather than assemble partial.
                    return null;
                }
                assembler.addImport(resolved);
            }
            var result = assembler.assemble();
            if (result.getValidationEvents()
                    .stream()
                    .anyMatch(ev -> ev.getSeverity() == Severity.ERROR)) {
                // The user's model itself doesn't assemble cleanly; that's their build's job to report, not ours.
                return null;
            }
            return result.unwrap();
        } catch (RuntimeException e) {
            // Any assembly failure => we cannot validate; abstain silently.
            return null;
        }
    }

    private Path resolveImportPath(String imp) {
        Path direct = Path.of(imp);
        if (direct.toFile().exists()) {
            return direct;
        }
        for (String root : importRoots) {
            Path candidate = Path.of(root).resolve(imp);
            if (candidate.toFile().exists()) {
                return candidate;
            }
        }
        return null;
    }

    /** Ordered, statically-known sources declared by an assembler chain. */
    private static final class AssemblerSources {
        private final Map<String, String> unparsed = new LinkedHashMap<>();
        private final List<String> imports = new ArrayList<>();
        private boolean discoverModels;
        private boolean sawAssembler;

        String cacheKey() {
            return "discover=" + discoverModels + ";unparsed=" + unparsed + ";imports=" + imports;
        }
    }
}
