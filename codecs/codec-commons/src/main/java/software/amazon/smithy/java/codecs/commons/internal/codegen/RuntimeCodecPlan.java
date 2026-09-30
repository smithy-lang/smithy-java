/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

/** Immutable generation-time plan for a schema graph. */
@SmithyInternalApi
public record RuntimeCodecPlan(
        Schema root,
        Class<?> rootClass,
        List<StructPlan> structures,
        int estimatedBytecode) {
    private static final int MEMBER_ESTIMATE = 36;

    public RuntimeCodecPlan {
        structures = List.copyOf(structures);
    }

    public static RuntimeCodecPlan analyze(Schema root) {
        return analyze(root, RuntimeCodecBackend.Budgets.unbounded());
    }

    public static RuntimeCodecPlan analyze(
            Schema root,
            RuntimeCodecBackend.Budgets budgets
    ) {
        return analyze(
                root,
                budgets,
                RuntimeCodecBackend.Mode.READ_WRITE,
                RuntimeCodecBackend.MemberSelector.all());
    }

    public static RuntimeCodecPlan analyze(
            Schema root,
            RuntimeCodecBackend.Budgets budgets,
            RuntimeCodecBackend.Mode mode,
            RuntimeCodecBackend.MemberSelector selector
    ) {
        Schema target = root.isMember() ? root.memberTarget() : root;
        Class<?> rootClass = requireShapeClass(target);
        var structures = new ArrayList<StructPlan>();
        var visited = new HashSet<ShapeId>();
        analyze(target, structures, visited, new Context(budgets, mode, selector, target));
        int estimate = structures.stream().mapToInt(StructPlan::estimatedBytecode).sum();
        RuntimeCodecPlan plan = new RuntimeCodecPlan(target, rootClass, structures, estimate);
        if (isNarrowed(plan) && plannedGraphReferencesRoot(plan)) {
            throw new UnsupportedSchemaException(
                    "Cannot narrow recursive root schema " + target.id());
        }
        return plan;
    }

    private static void analyze(
            Schema schema,
            List<StructPlan> structures,
            Set<ShapeId> visited,
            Context context
    ) {
        schema = schema.isMember() ? schema.memberTarget() : schema;
        if (!visited.add(schema.id())) {
            return;
        }

        ShapeType type = schema.type();
        if (type != ShapeType.STRUCTURE && type != ShapeType.UNION) {
            analyzeChildren(schema, structures, visited, context);
            return;
        }

        Class<?> shapeClass = requireShapeClass(schema);
        boolean union = type == ShapeType.UNION || (shapeClass.isInterface() && shapeClass.isSealed());
        // Write-only backends do not require builder metadata.
        boolean reads = context.mode() == RuntimeCodecBackend.Mode.READ_WRITE;
        Class<?> builderClass = null;
        Method builderFactory = null;
        if (reads) {
            ShapeBuilder<?> builder = schema.shapeBuilder();
            if (builder == null) {
                throw new UnsupportedSchemaException("No builder for " + schema.id());
            }
            builderClass = builder.getClass();
            requirePubliclyAccessible(builderClass, "Generated Java builder", schema);
            builderFactory = resolveBuilderFactory(shapeClass);
        }
        var members = new ArrayList<MemberPlan>(schema.members().size());
        var getters = new HashMap<Method, Schema>();
        var setters = new HashMap<Method, Schema>();
        int estimate = 24;
        // Only the root may be narrowed.
        boolean isRoot = schema == context.rootTarget();
        for (Schema member : schema.members()) {
            if (isRoot && !context.selector().select(schema, member)) {
                continue;
            }
            Schema target = member.memberTarget();
            Method getter = union ? null : resolveGetter(shapeClass, member);
            Method presence = union ? null : resolvePresence(shapeClass, member);
            Class<?> unionVariant = union ? resolveUnionVariant(shapeClass, member) : null;
            Method unionAccessor = unionVariant == null ? null : resolveUnionAccessor(unionVariant, member);
            Class<?> memberType = unionAccessor == null
                    ? getter.getReturnType()
                    : unionAccessor.getReturnType();
            validateAccessorType(member, memberType);
            Method setter = reads
                    ? resolveSetter(
                            builderClass,
                            member,
                            unionAccessor == null ? null : unionAccessor.getName(),
                            memberType)
                    : null;
            rejectAccessorCollision(getters, getter, member, "getter");
            rejectAccessorCollision(setters, setter, member, "setter");
            int memberEstimate = estimateMember(target);
            estimate += memberEstimate;
            members.add(new MemberPlan(
                    member,
                    target,
                    member.memberName(),
                    getter,
                    presence,
                    setter,
                    unionVariant,
                    unionAccessor,
                    member.hasTrait(TraitKey.REQUIRED_TRAIT),
                    memberEstimate));
            analyze(target, structures, visited, context);
        }
        RuntimeCodecBackend.Budgets budgets = context.budgets();
        List<MethodRange> writerChunks = chunkRanges(
                members,
                budgets.writerBytecodeLimit(),
                budgets.maxMembersPerWriterMethod());
        int readerBuckets = Math.max(
                divideRoundingUp(estimate, budgets.readerBytecodeLimit()),
                divideRoundingUp(members.size(), budgets.maxMembersPerReaderBucket()));
        structures.add(new StructPlan(
                schema,
                shapeClass,
                builderClass,
                builderFactory,
                union,
                members,
                estimate,
                writerChunks,
                readerBuckets));
    }

    private static void analyzeChildren(
            Schema schema,
            List<StructPlan> structures,
            Set<ShapeId> visited,
            Context context
    ) {
        switch (schema.type()) {
            case LIST, SET -> analyze(schema.listMember(), structures, visited, context);
            case MAP -> analyze(schema.mapValueMember(), structures, visited, context);
            default -> {
            }
        }
    }

    private static boolean isNarrowed(RuntimeCodecPlan plan) {
        ShapeType type = plan.root().type();
        return (type == ShapeType.STRUCTURE || type == ShapeType.UNION)
                && plan.rootStructure().members().size() != plan.root().members().size();
    }

    private static boolean plannedGraphReferencesRoot(RuntimeCodecPlan plan) {
        ShapeId rootId = plan.root().id();
        for (StructPlan structure : plan.structures()) {
            for (MemberPlan member : structure.members()) {
                if (references(member.target(), rootId, new HashSet<>())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean references(Schema schema, ShapeId target, Set<ShapeId> visited) {
        schema = schema.isMember() ? schema.memberTarget() : schema;
        if (schema.id().equals(target)) {
            return true;
        }
        if (!visited.add(schema.id())) {
            return false;
        }
        return switch (schema.type()) {
            case LIST, SET -> references(schema.listMember(), target, visited);
            case MAP -> references(schema.mapValueMember(), target, visited);
            default -> false;
        };
    }

    private record Context(
            RuntimeCodecBackend.Budgets budgets,
            RuntimeCodecBackend.Mode mode,
            RuntimeCodecBackend.MemberSelector selector,
            Schema rootTarget) {}

    private static List<MethodRange> chunkRanges(
            List<MemberPlan> members,
            int bytecodeLimit,
            int maxMembers
    ) {
        var chunks = new ArrayList<MethodRange>();
        int start = 0;
        int size = 0;
        int count = 0;
        for (int i = 0; i < members.size(); i++) {
            MemberPlan member = members.get(i);
            if (count > 0
                    && (count == maxMembers
                            || size + member.estimatedBytecode() > bytecodeLimit)) {
                chunks.add(new MethodRange(start, i, size));
                start = i;
                size = 0;
                count = 0;
            }
            size += member.estimatedBytecode();
            count++;
        }
        chunks.add(new MethodRange(start, members.size(), size));
        return chunks;
    }

    private static int divideRoundingUp(int value, int divisor) {
        return value == 0 ? 1 : 1 + (value - 1) / divisor;
    }

    private static int estimateMember(Schema target) {
        return switch (target.type()) {
            case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, INT_ENUM -> 24;
            case STRING -> 60;
            case ENUM, BLOB, TIMESTAMP -> 36;
            case BIG_INTEGER, BIG_DECIMAL, DOCUMENT -> 48;
            case LIST, SET, MAP -> 72;
            case STRUCTURE, UNION -> 44;
            default -> MEMBER_ESTIMATE;
        };
    }

    private static Class<?> requireShapeClass(Schema schema) {
        Class<?> result = schema.shapeClass();
        if (result == null) {
            throw new UnsupportedSchemaException("No generated Java class for " + schema.id());
        }
        requirePubliclyAccessible(result, "Generated Java class", schema);
        return result;
    }

    private static void requirePubliclyAccessible(Class<?> type, String description, Schema schema) {
        for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
            if (!Modifier.isPublic(current.getModifiers())) {
                throw new UnsupportedSchemaException(
                        description + " is not publicly accessible for " + schema.id() + ": " + type.getName());
            }
        }
    }

    private static void rejectAccessorCollision(
            Map<Method, Schema> resolved,
            Method accessor,
            Schema member,
            String kind
    ) {
        if (accessor == null) {
            return;
        }
        Schema previous = resolved.putIfAbsent(accessor, member);
        if (previous != null) {
            throw new UnsupportedSchemaException(
                    "Members "
                            + previous.id()
                            + " and "
                            + member.id()
                            + " resolve to the same "
                            + kind
                            + ": "
                            + accessor);
        }
    }

    private static Method resolveGetter(Class<?> shapeClass, Schema member) {
        boolean isBoolean = member.memberTarget().type() == ShapeType.BOOLEAN;
        List<String> candidates = new ArrayList<>(6);
        // Try generated Java naming first, then the raw name for hand-written shapes.
        addAccessorCandidates(candidates, toJavaName(member.memberName()), isBoolean);
        addAccessorCandidates(candidates, member.memberName(), isBoolean);
        for (String candidate : candidates) {
            try {
                Method method = shapeClass.getMethod(candidate);
                if (Modifier.isPublic(method.getModifiers())
                        && method.getParameterCount() == 0
                        // Never bind model members to Object or schema API methods.
                        && method.getDeclaringClass() != Object.class
                        && method.getReturnType() != Schema.class) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {}
        }
        throw new UnsupportedSchemaException(
                "No direct getter for " + member.id() + " on " + shapeClass.getName());
    }

    private static void addAccessorCandidates(List<String> candidates, String name, boolean isBoolean) {
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        if (isBoolean) {
            addCandidate(candidates, "is" + capitalized);
        }
        addCandidate(candidates, "get" + capitalized);
        addCandidate(candidates, name);
    }

    private static void addCandidate(List<String> candidates, String candidate) {
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private static Method resolvePresence(Class<?> shapeClass, Schema member) {
        // Presence methods use the same generated name as accessors.
        Method method = findPresence(shapeClass, toJavaName(member.memberName()));
        return method != null ? method : findPresence(shapeClass, member.memberName());
    }

    private static Method findPresence(Class<?> shapeClass, String name) {
        String candidate = "has" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method method = shapeClass.getMethod(candidate);
            return method.getReturnType() == boolean.class && method.getParameterCount() == 0 ? method : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveBuilderFactory(Class<?> shapeClass) {
        try {
            Method method = shapeClass.getDeclaredMethod("builder");
            return Modifier.isPublic(method.getModifiers()) ? method : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Class<?> resolveUnionVariant(Class<?> shapeClass, Schema member) {
        String normalized = normalizeName(member.memberName());
        for (Class<?> candidate : shapeClass.getPermittedSubclasses()) {
            if (!candidate.isRecord() || candidate.getRecordComponents().length != 1) {
                continue;
            }
            if (normalizeName(candidate.getRecordComponents()[0].getName()).equals(normalized)) {
                return candidate;
            }
        }
        throw new UnsupportedSchemaException(
                "No direct union variant for " + member.id() + " on " + shapeClass.getName());
    }

    private static Method resolveUnionAccessor(Class<?> variant, Schema member) {
        if (variant.getRecordComponents().length == 1) {
            return variant.getRecordComponents()[0].getAccessor();
        }
        throw new UnsupportedSchemaException(
                "No union accessor for " + member.id() + " on " + variant.getName());
    }

    private static Method resolveSetter(
            Class<?> builderClass,
            Schema member,
            String unionAccessor,
            Class<?> memberType
    ) {
        String expected = unionAccessor == null ? toJavaName(member.memberName()) : unionAccessor;
        Method match = null;
        for (Method method : builderClass.getMethods()) {
            if (!method.getName().equals(expected)
                    || method.getParameterCount() != 1
                    || !Modifier.isPublic(method.getModifiers())
                    || method.getParameterTypes()[0] != memberType) {
                continue;
            }
            if (match == null
                    || (match.isBridge() && !method.isBridge())
                    || (match.isSynthetic() && !method.isSynthetic())) {
                match = method;
            }
        }
        if (match != null) {
            return match;
        }
        throw new UnsupportedSchemaException(
                "No direct builder setter for "
                        + member.id()
                        + " accepting "
                        + memberType.getName()
                        + " on "
                        + builderClass.getName());
    }

    private static void validateAccessorType(Schema member, Class<?> declaredType) {
        Schema target = member.memberTarget();
        boolean compatible = switch (target.type()) {
            case BOOLEAN -> isPrimitiveOrBoxed(declaredType, boolean.class, Boolean.class);
            case BYTE -> isPrimitiveOrBoxed(declaredType, byte.class, Byte.class);
            case SHORT -> isPrimitiveOrBoxed(declaredType, short.class, Short.class);
            case INTEGER -> isPrimitiveOrBoxed(declaredType, int.class, Integer.class);
            case LONG -> isPrimitiveOrBoxed(declaredType, long.class, Long.class);
            case FLOAT -> isPrimitiveOrBoxed(declaredType, float.class, Float.class);
            case DOUBLE -> isPrimitiveOrBoxed(declaredType, double.class, Double.class);
            case BIG_INTEGER -> BigInteger.class.isAssignableFrom(declaredType);
            case BIG_DECIMAL -> BigDecimal.class.isAssignableFrom(declaredType);
            case STRING -> String.class.isAssignableFrom(declaredType);
            case ENUM -> SmithyEnum.class.isAssignableFrom(declaredType);
            case INT_ENUM -> SmithyIntEnum.class.isAssignableFrom(declaredType);
            case BLOB -> ByteBuffer.class.isAssignableFrom(declaredType);
            case TIMESTAMP -> Instant.class.isAssignableFrom(declaredType);
            case DOCUMENT -> Document.class.isAssignableFrom(declaredType);
            case LIST, SET -> List.class.isAssignableFrom(declaredType);
            case MAP -> Map.class.isAssignableFrom(declaredType);
            case STRUCTURE, UNION -> requireShapeClass(target).isAssignableFrom(declaredType);
            default -> false;
        };
        if (!compatible) {
            throw new UnsupportedSchemaException(
                    "Accessor for "
                            + member.id()
                            + " returns "
                            + declaredType.getTypeName()
                            + ", which is incompatible with "
                            + target.type());
        }
    }

    private static boolean isPrimitiveOrBoxed(
            Class<?> declaredType,
            Class<?> primitive,
            Class<?> boxed
    ) {
        return declaredType == primitive || declaredType == boxed;
    }

    // Mirrors generated accessor naming, including acronym folding.
    private static String toJavaName(String value) {
        if (value.indexOf('_') < 0 && value.indexOf('-') < 0 && value.indexOf(' ') < 0) {
            if (Character.isLowerCase(value.charAt(0))) {
                return value;
            }
            if (value.equals(value.toUpperCase(Locale.ROOT))) {
                return value.toLowerCase(Locale.ROOT);
            }
            StringBuilder folded = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                boolean nextIsUpperCase = i + 1 < value.length() && Character.isUpperCase(value.charAt(i + 1));
                if (Character.isUpperCase(c) && (nextIsUpperCase || i == 0)) {
                    folded.append(Character.toLowerCase(c));
                } else {
                    folded.append(c);
                }
            }
            return folded.toString();
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '-' || c == '_' || c == ' ') {
                capitalize = true;
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else if (result.isEmpty()) {
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String normalizeName(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                result.append(Character.toLowerCase(current));
            }
        }
        if (result.toString().endsWith("member")) {
            result.setLength(result.length() - "member".length());
        }
        return result.toString();
    }

    public StructPlan rootStructure() {
        for (StructPlan structure : structures) {
            if (structure.schema().id().equals(root.id())) {
                return structure;
            }
        }
        throw new UnsupportedSchemaException("Root is not a structure or union: " + root.id());
    }

    public record StructPlan(
            Schema schema,
            Class<?> shapeClass,
            Class<?> builderClass,
            Method builderFactory,
            boolean union,
            List<MemberPlan> members,
            int estimatedBytecode,
            List<MethodRange> writerChunks,
            int readerBuckets) {
        public StructPlan {
            members = List.copyOf(members);
            writerChunks = List.copyOf(writerChunks);
        }
    }

    public record MethodRange(int startInclusive, int endExclusive, int estimatedBytecode) {}

    public record MemberPlan(
            Schema schema,
            Schema target,
            String memberName,
            Method getter,
            Method presence,
            Method setter,
            Class<?> unionVariant,
            Method unionAccessor,
            boolean required,
            int estimatedBytecode) {}
}
