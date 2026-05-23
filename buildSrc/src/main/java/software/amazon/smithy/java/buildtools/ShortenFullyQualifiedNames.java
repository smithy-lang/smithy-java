/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.buildtools;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.diffplug.spotless.FormatterFunc;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.SerializedFunction;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Replaces unnecessary fully qualified type names with simple names and adds the corresponding
 * import statements.
 */
public final class ShortenFullyQualifiedNames {

    private static final String STEP_NAME = "shortenFullyQualifiedNames";

    private ShortenFullyQualifiedNames() {}

    public static FormatterStep createStep() {
        return FormatterStep.create(
                STEP_NAME,
                new State(),
                SerializedFunction.identity(),
                State::toFormatter);
    }

    private static final class State implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        FormatterFunc toFormatter() {
            return ShortenFullyQualifiedNames::shortenNames;
        }
    }

    private record QualifiedNameUsage(
            String fullyQualifiedName,
            String simpleName,
            String qualifier,
            int startPos,
            int endPos,
            String replacementText) {}

    public static String shortenNames(String contents) {
        CompilationUnitTree unit;
        SourcePositions sourcePositions;
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return contents;
            }
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavaFileObject source = new SimpleJavaFileObject(
                    URI.create("string:///Source.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return contents;
                }
            };
            JavacTask task = (JavacTask) compiler.getTask(
                    null, null, diagnostics,
                    List.of("--enable-preview", "--source", String.valueOf(Runtime.version().feature())),
                    null, List.of(source));
            Iterable<? extends CompilationUnitTree> units = task.parse();
            unit = units.iterator().next();
            sourcePositions = Trees.instance(task).getSourcePositions();

            boolean hasErrors = diagnostics.getDiagnostics().stream()
                    .anyMatch(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR);
            if (hasErrors) {
                return contents;
            }
        } catch (IOException e) {
            return contents;
        }

        String currentPackage = unit.getPackageName() != null
                ? unit.getPackageName().toString() : "";

        Map<String, String> existingImports = new LinkedHashMap<>();
        Set<String> wildcardImportPackages = new HashSet<>();
        Set<String> staticImportedSimpleNames = new HashSet<>();

        for (ImportTree importTree : unit.getImports()) {
            if (importTree.isStatic()) {
                String fqn = importTree.getQualifiedIdentifier().toString();
                int lastDot = fqn.lastIndexOf('.');
                if (lastDot >= 0) {
                    staticImportedSimpleNames.add(fqn.substring(lastDot + 1));
                }
                continue;
            }
            String fqn = importTree.getQualifiedIdentifier().toString();
            if (fqn.endsWith(".*")) {
                wildcardImportPackages.add(fqn.substring(0, fqn.length() - 2));
            } else {
                int lastDot = fqn.lastIndexOf('.');
                if (lastDot >= 0) {
                    String simpleName = fqn.substring(lastDot + 1);
                    existingImports.put(simpleName, fqn);
                }
            }
        }

        Set<String> locallyDefinedTypes = new HashSet<>();
        collectLocalTypes(unit, locallyDefinedTypes);

        Set<String> implicitlyUsedNames = new HashSet<>();
        collectImplicitlyUsedNames(unit, existingImports.keySet(), implicitlyUsedNames);

        QualifiedNameScanner scanner = new QualifiedNameScanner(unit, sourcePositions);
        scanner.scan(unit, null);

        List<QualifiedNameUsage> usages = scanner.getUsages();
        if (usages.isEmpty()) {
            return contents;
        }

        Map<String, List<QualifiedNameUsage>> bySimpleName = new LinkedHashMap<>();
        for (QualifiedNameUsage usage : usages) {
            bySimpleName.computeIfAbsent(usage.simpleName(), k -> new ArrayList<>()).add(usage);
        }

        TreeMap<Integer, Replacement> replacements = new TreeMap<>();
        Set<String> importsToAdd = new HashSet<>();

        for (Map.Entry<String, List<QualifiedNameUsage>> entry : bySimpleName.entrySet()) {
            String simpleName = entry.getKey();
            List<QualifiedNameUsage> group = entry.getValue();

            if (locallyDefinedTypes.contains(simpleName)) {
                continue;
            }

            if (staticImportedSimpleNames.contains(simpleName)) {
                continue;
            }

            if (implicitlyUsedNames.contains(simpleName) && !existingImports.containsKey(simpleName)) {
                continue;
            }

            Map<String, List<QualifiedNameUsage>> byFqn = new LinkedHashMap<>();
            for (QualifiedNameUsage usage : group) {
                byFqn.computeIfAbsent(usage.fullyQualifiedName(), k -> new ArrayList<>()).add(usage);
            }

            if (existingImports.containsKey(simpleName)) {
                String importedFqn = existingImports.get(simpleName);
                List<QualifiedNameUsage> matching = byFqn.get(importedFqn);
                if (matching != null) {
                    for (QualifiedNameUsage usage : matching) {
                        replacements.put(usage.startPos(), new Replacement(usage.startPos(), usage.endPos(), usage.replacementText()));
                    }
                }
                continue;
            }

            String fqnToShorten = selectFqnToShorten(byFqn);
            List<QualifiedNameUsage> toShorten = byFqn.get(fqnToShorten);
            for (QualifiedNameUsage usage : toShorten) {
                replacements.put(usage.startPos(), new Replacement(usage.startPos(), usage.endPos(), usage.replacementText()));
            }
            if (needsImport(toShorten.getFirst().qualifier(), currentPackage, wildcardImportPackages)) {
                importsToAdd.add(fqnToShorten);
            }
        }

        if (replacements.isEmpty()) {
            return contents;
        }

        String result = applyReplacements(contents, replacements);
        result = addImports(result, importsToAdd);
        return result;
    }

    private static String selectFqnToShorten(Map<String, List<QualifiedNameUsage>> byFqn) {
        if (byFqn.size() == 1) {
            return byFqn.keySet().iterator().next();
        }
        String mostUsedFqn = null;
        int maxCount = 0;
        for (Map.Entry<String, List<QualifiedNameUsage>> fqnEntry : byFqn.entrySet()) {
            int count = fqnEntry.getValue().size();
            if (count > maxCount || (count == maxCount && (mostUsedFqn == null
                    || fqnEntry.getKey().compareTo(mostUsedFqn) < 0))) {
                maxCount = count;
                mostUsedFqn = fqnEntry.getKey();
            }
        }
        return mostUsedFqn;
    }

    private record Replacement(int startPos, int endPos, String text) {}

    private static boolean needsImport(String qualifier, String currentPackage,
            Set<String> wildcardImportPackages) {
        return !qualifier.equals("java.lang")
                && (currentPackage.isEmpty() || !qualifier.equals(currentPackage))
                && !wildcardImportPackages.contains(qualifier);
    }

    private static void collectLocalTypes(CompilationUnitTree unit, Set<String> types) {
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void unused) {
                if (node.getSimpleName() != null && !node.getSimpleName().toString().isEmpty()) {
                    types.add(node.getSimpleName().toString());
                }
                return super.visitClass(node, unused);
            }
        }.scan(unit, null);
    }

    private static void collectImplicitlyUsedNames(CompilationUnitTree unit,
            Set<String> explicitlyImportedNames, Set<String> result) {
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitImport(ImportTree node, Void unused) {
                return null;
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void unused) {
                String name = node.getName().toString();
                TreePath parentPath = getCurrentPath().getParentPath();
                if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))
                        && !explicitlyImportedNames.contains(name)
                        && parentPath != null
                        && isTypePosition(parentPath.getLeaf(), node)) {
                    result.add(name);
                }
                return super.visitIdentifier(node, unused);
            }
        }.scan(unit, null);
    }

    private static boolean isTypePosition(Tree parent, Tree node) {
        return switch (parent) {
            case VariableTree v -> v.getType() == node;
            case MethodTree m -> m.getReturnType() == node || m.getThrows().contains((ExpressionTree) node);
            case NewClassTree n -> n.getIdentifier() == node;
            case TypeCastTree t -> t.getType() == node;
            case InstanceOfTree i -> i.getType() == node;
            case ParameterizedTypeTree p -> p.getType() == node || p.getTypeArguments().contains(node);
            case AnnotationTree a -> a.getAnnotationType() == node;
            case ClassTree c -> c.getExtendsClause() == node
                    || c.getImplementsClause().contains(node)
                    || c.getPermitsClause().contains(node);
            case TypeParameterTree t -> t.getBounds().contains(node);
            case WildcardTree ignored -> true;
            case ArrayTypeTree a -> a.getType() == node;
            case AnnotatedTypeTree a -> a.getUnderlyingType() == node;
            default -> false;
        };
    }

    private static final class QualifiedNameScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions sourcePositions;
        private final List<QualifiedNameUsage> usages = new ArrayList<>();

        QualifiedNameScanner(CompilationUnitTree unit, SourcePositions sourcePositions) {
            this.unit = unit;
            this.sourcePositions = sourcePositions;
        }

        List<QualifiedNameUsage> getUsages() {
            return usages;
        }

        @Override
        public Void visitImport(ImportTree node, Void unused) {
            return null;
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            TreePath parentPath = getCurrentPath().getParentPath();
            if (parentPath == null) {
                return super.visitMemberSelect(node, unused);
            }
            Tree parent = parentPath.getLeaf();

            if (parent instanceof MemberSelectTree) {
                return super.visitMemberSelect(node, unused);
            }

            // Skip qualified names inside AnnotatedTypeTree (e.g., java.util.@NonNull List)
            // since the annotation is interleaved in the source text and replacing would drop it
            if (parent instanceof AnnotatedTypeTree) {
                return super.visitMemberSelect(node, unused);
            }

            if (isTypePosition(parent, node)) {
                tryRecordQualifiedName(node, true);
            } else if (!(parent instanceof MethodInvocationTree) && !(parent instanceof MemberReferenceTree)) {
                tryRecordQualifiedName(node, false);
            }

            return super.visitMemberSelect(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            ExpressionTree methodSelect = node.getMethodSelect();
            if (methodSelect instanceof MemberSelectTree selectTree) {
                ExpressionTree expression = selectTree.getExpression();
                if (expression instanceof MemberSelectTree memberSelect) {
                    tryRecordQualifiedName(memberSelect, false);
                }
            }
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            ExpressionTree qualifier = node.getQualifierExpression();
            if (qualifier instanceof MemberSelectTree memberSelect) {
                tryRecordQualifiedName(memberSelect, false);
            }
            return super.visitMemberReference(node, unused);
        }

        /**
         * @param requireFullMatch if true, the type prefix must span the entire expression
         *                         (used for type positions like variable types, extends clauses).
         *                         If false, the type prefix may be a prefix of the expression
         *                         (used for static field access/method calls/references).
         */
        private void tryRecordQualifiedName(MemberSelectTree node, boolean requireFullMatch) {
            List<String> parts = flattenMemberSelect(node);
            if (parts.size() < 2) {
                return;
            }

            if (!Character.isLowerCase(parts.getFirst().charAt(0))) {
                return;
            }

            Optional<Integer> typePrefixLen = typePrefixLength(parts);
            if (typePrefixLen.isEmpty()) {
                return;
            }

            int typeEndIdx = typePrefixLen.get();
            if (requireFullMatch) {
                if (typeEndIdx != parts.size() - 1) {
                    return;
                }
            } else {
                if (typeEndIdx < 1) {
                    return;
                }
            }

            int firstTypeIdx = findFirstTypeIndex(parts);
            if (firstTypeIdx < 1) {
                return;
            }

            String qualifier = String.join(".", parts.subList(0, firstTypeIdx));
            String topLevelType = parts.get(firstTypeIdx);
            int replacementEnd = requireFullMatch ? parts.size() : typeEndIdx + 1;
            String fqnForImport = qualifier + "." + topLevelType;
            String replacementText = (firstTypeIdx == replacementEnd - 1)
                    ? topLevelType
                    : String.join(".", parts.subList(firstTypeIdx, replacementEnd));

            Tree rootNode = findRootOfChain(node);
            long startLong = sourcePositions.getStartPosition(unit, rootNode);
            long endLong = sourcePositions.getEndPosition(unit, node);

            if (startLong < 0 || endLong < 0 || startLong >= endLong) {
                return;
            }

            usages.add(new QualifiedNameUsage(fqnForImport, topLevelType, qualifier,
                    (int) startLong, (int) endLong, replacementText));
        }

        private int findFirstTypeIndex(List<String> parts) {
            for (int i = 0; i < parts.size(); i++) {
                if (Character.isUpperCase(parts.get(i).charAt(0))) {
                    return i;
                }
            }
            return -1;
        }

        private Tree findRootOfChain(MemberSelectTree node) {
            ExpressionTree expr = node;
            while (expr instanceof MemberSelectTree select) {
                ExpressionTree inner = select.getExpression();
                if (inner instanceof IdentifierTree) {
                    return inner;
                } else if (inner instanceof MemberSelectTree) {
                    expr = inner;
                } else {
                    break;
                }
            }
            return expr;
        }

        private List<String> flattenMemberSelect(ExpressionTree expr) {
            List<String> parts = new ArrayList<>();
            while (expr instanceof MemberSelectTree select) {
                parts.addFirst(select.getIdentifier().toString());
                expr = select.getExpression();
            }
            if (expr instanceof IdentifierTree ident) {
                parts.addFirst(ident.getName().toString());
            }
            return parts;
        }
    }

    private static String applyReplacements(String source, TreeMap<Integer, Replacement> replacements) {
        StringBuilder sb = new StringBuilder(source);
        int offset = 0;
        for (Replacement replacement : replacements.values()) {
            int start = offset + replacement.startPos();
            int end = offset + replacement.endPos();
            sb.replace(start, end, replacement.text());
            offset += replacement.text().length() - (replacement.endPos() - replacement.startPos());
        }
        return sb.toString();
    }

    private static String addImports(String result, Set<String> importsToAdd) {
        if (importsToAdd.isEmpty()) {
            return result;
        }

        String sep = guessLineSeparator(result);
        StringBuilder importBlock = new StringBuilder();
        List<String> sorted = new ArrayList<>(importsToAdd);
        sorted.sort(String::compareTo);
        for (String fqn : sorted) {
            importBlock.append("import ").append(fqn).append(";").append(sep);
        }

        int insertionPoint = findImportInsertionPoint(result, sep);

        String after = result.substring(insertionPoint);
        if (!after.startsWith(sep)) {
            importBlock.append(sep);
        }

        return result.substring(0, insertionPoint) + importBlock + after;
    }

    private static String guessLineSeparator(String source) {
        int crlfCount = 0;
        int lfCount = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                if (i > 0 && source.charAt(i - 1) == '\r') {
                    crlfCount++;
                } else {
                    lfCount++;
                }
            }
        }
        return crlfCount > lfCount ? "\r\n" : "\n";
    }

    private static int findImportInsertionPoint(String source, String sep) {
        int lastImportEnd = -1;
        int idx = 0;
        while (idx < source.length()) {
            int lineEnd = source.indexOf(sep, idx);
            if (lineEnd == -1) {
                lineEnd = source.length();
            }
            String line = source.substring(idx, lineEnd).trim();
            if (line.startsWith("import ") && line.endsWith(";")) {
                int candidateEnd = lineEnd + sep.length();
                lastImportEnd = Math.min(candidateEnd, source.length());
            }
            if (line.startsWith("public class ") || line.startsWith("class ")
                    || line.startsWith("public interface ") || line.startsWith("interface ")
                    || line.startsWith("public enum ") || line.startsWith("enum ")
                    || line.startsWith("public @interface ") || line.startsWith("@interface ")
                    || line.startsWith("public abstract class ")
                    || line.startsWith("public final class ")
                    || line.startsWith("abstract class ")
                    || line.startsWith("final class ")) {
                break;
            }
            idx = lineEnd + sep.length();
            if (idx <= lineEnd) {
                break;
            }
        }

        if (lastImportEnd > 0) {
            return lastImportEnd;
        }

        int packageEnd = source.indexOf(";");
        if (packageEnd >= 0) {
            String beforeSemicolon = source.substring(0, packageEnd).trim();
            if (beforeSemicolon.startsWith("package ")) {
                int afterPackage = packageEnd + 1;
                while (afterPackage + sep.length() <= source.length()
                        && source.startsWith(sep, afterPackage)) {
                    afterPackage += sep.length();
                }
                return afterPackage;
            }
        }

        return 0;
    }

    // --- Type name classification (ported from google-java-format's TypeNameClassifier) ---

    private static Optional<Integer> typePrefixLength(List<String> nameParts) {
        TyParseState state = TyParseState.START;
        Optional<Integer> typeLength = Optional.empty();
        for (int i = 0; i < nameParts.size(); i++) {
            state = state.next(JavaCaseFormat.from(nameParts.get(i)));
            if (state == TyParseState.REJECT) {
                break;
            }
            if (state.isSingleUnit()) {
                typeLength = Optional.of(i);
            }
        }
        return typeLength;
    }

    private sealed interface TyParseState {
        TyParseState next(JavaCaseFormat n);

        boolean isSingleUnit();

        TyParseState START = new Start();
        TyParseState TYPE = new Type();
        TyParseState FIRST_STATIC_MEMBER = new FirstStaticMember();
        TyParseState REJECT = new Reject();
        TyParseState AMBIGUOUS = new Ambiguous();

        record Start() implements TyParseState {
            @Override
            public TyParseState next(JavaCaseFormat n) {
                return switch (n) {
                    case UPPERCASE -> AMBIGUOUS;
                    case LOWER_CAMEL -> REJECT;
                    case LOWERCASE -> START;
                    case UPPER_CAMEL -> TYPE;
                };
            }

            @Override
            public boolean isSingleUnit() {
                return false;
            }
        }

        record Type() implements TyParseState {
            @Override
            public TyParseState next(JavaCaseFormat n) {
                return switch (n) {
                    case UPPERCASE, LOWER_CAMEL, LOWERCASE -> FIRST_STATIC_MEMBER;
                    case UPPER_CAMEL -> TYPE;
                };
            }

            @Override
            public boolean isSingleUnit() {
                return true;
            }
        }

        record FirstStaticMember() implements TyParseState {
            @Override
            public TyParseState next(JavaCaseFormat n) {
                return REJECT;
            }

            @Override
            public boolean isSingleUnit() {
                return true;
            }
        }

        record Reject() implements TyParseState {
            @Override
            public TyParseState next(JavaCaseFormat n) {
                return REJECT;
            }

            @Override
            public boolean isSingleUnit() {
                return false;
            }
        }

        record Ambiguous() implements TyParseState {
            @Override
            public TyParseState next(JavaCaseFormat n) {
                return switch (n) {
                    case UPPERCASE -> AMBIGUOUS;
                    case LOWER_CAMEL, LOWERCASE -> REJECT;
                    case UPPER_CAMEL -> TYPE;
                };
            }

            @Override
            public boolean isSingleUnit() {
                return false;
            }
        }
    }

    private enum JavaCaseFormat {
        UPPERCASE,
        LOWERCASE,
        UPPER_CAMEL,
        LOWER_CAMEL;

        static JavaCaseFormat from(String name) {
            boolean firstUppercase = false;
            boolean hasUppercase = false;
            boolean hasLowercase = false;
            boolean first = true;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (!Character.isAlphabetic(c)) {
                    continue;
                }
                if (first) {
                    firstUppercase = Character.isUpperCase(c);
                    first = false;
                }
                hasUppercase |= Character.isUpperCase(c);
                hasLowercase |= Character.isLowerCase(c);
            }
            if (firstUppercase) {
                return hasLowercase ? UPPER_CAMEL : UPPERCASE;
            } else {
                return hasUppercase ? LOWER_CAMEL : LOWERCASE;
            }
        }
    }
}
