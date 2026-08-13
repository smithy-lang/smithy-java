/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Immutable generation-time plan for a root schema graph.
 */
public record RuntimeCodecPlan(
        Schema root,
        Class<?> rootClass,
        List<StructPlan> structures,
        int estimatedBytecode) {
    private static final int MEMBER_ESTIMATE = 36;
    private static final int WRITER_SPLIT_ESTIMATE = 220;
    private static final int READER_SPLIT_ESTIMATE = 300;
    private static final int MAX_MEMBERS_PER_WRITER_METHOD = 8;

    public RuntimeCodecPlan {
        structures = List.copyOf(structures);
    }

    public static RuntimeCodecPlan analyze(Schema root) {
        Schema target = root.isMember() ? root.memberTarget() : root;
        Class<?> rootClass = requireShapeClass(target);
        var structures = new ArrayList<StructPlan>();
        var visited = new HashSet<ShapeId>();
        analyze(target, structures, visited);
        int estimate = structures.stream().mapToInt(StructPlan::estimatedBytecode).sum();
        return new RuntimeCodecPlan(target, rootClass, structures, estimate);
    }

    private static void analyze(
            Schema schema,
            List<StructPlan> structures,
            Set<ShapeId> visited
    ) {
        schema = schema.isMember() ? schema.memberTarget() : schema;
        if (!visited.add(schema.id())) {
            return;
        }

        ShapeType type = schema.type();
        if (type != ShapeType.STRUCTURE && type != ShapeType.UNION) {
            analyzeChildren(schema, structures, visited);
            return;
        }

        Class<?> shapeClass = requireShapeClass(schema);
        boolean union = type == ShapeType.UNION || (shapeClass.isInterface() && shapeClass.isSealed());
        ShapeBuilder<?> builder = schema.shapeBuilder();
        if (builder == null) {
            throw new UnsupportedSchemaException("No builder for " + schema.id());
        }
        Class<?> builderClass = builder.getClass();
        Method builderFactory = resolveBuilderFactory(shapeClass);
        var members = new ArrayList<MemberPlan>(schema.members().size());
        int estimate = 24;
        for (Schema member : schema.members()) {
            Schema target = member.memberTarget();
            Method getter = union ? null : resolveGetter(shapeClass, member);
            Method presence = union ? null : resolvePresence(shapeClass, member);
            Class<?> unionVariant = union ? resolveUnionVariant(shapeClass, member) : null;
            Method unionAccessor = unionVariant == null ? null : resolveUnionAccessor(unionVariant, member);
            Method setter = resolveSetter(
                    builderClass,
                    member,
                    unionAccessor == null ? null : unionAccessor.getName());
            String jsonName = member.hasTrait(TraitKey.JSON_NAME_TRAIT)
                    ? member.expectTrait(TraitKey.JSON_NAME_TRAIT).getValue()
                    : null;
            int memberEstimate = estimateMember(target);
            estimate += memberEstimate;
            members.add(new MemberPlan(
                    member,
                    target,
                    member.memberName(),
                    jsonName,
                    getter,
                    presence,
                    setter,
                    unionVariant,
                    unionAccessor,
                    member.hasTrait(TraitKey.REQUIRED_TRAIT),
                    memberEstimate));
            analyze(target, structures, visited);
        }
        List<MethodRange> writerChunks = chunkRanges(members, WRITER_SPLIT_ESTIMATE);
        int readerBuckets = Math.max(1, (estimate + READER_SPLIT_ESTIMATE - 1) / READER_SPLIT_ESTIMATE);
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
            Set<ShapeId> visited
    ) {
        switch (schema.type()) {
            case LIST, SET -> analyze(schema.listMember(), structures, visited);
            case MAP -> analyze(schema.mapValueMember(), structures, visited);
            default -> {
                // Scalar.
            }
        }
    }

    private static List<MethodRange> chunkRanges(List<MemberPlan> members, int bytecodeLimit) {
        var chunks = new ArrayList<MethodRange>();
        int start = 0;
        int size = 0;
        int count = 0;
        for (int i = 0; i < members.size(); i++) {
            MemberPlan member = members.get(i);
            if (count > 0
                    && (count == MAX_MEMBERS_PER_WRITER_METHOD
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
        return result;
    }

    private static Method resolveGetter(Class<?> shapeClass, Schema member) {
        String name = member.memberName();
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        List<String> candidates = new ArrayList<>(4);
        if (member.memberTarget().type() == ShapeType.BOOLEAN) {
            candidates.add("is" + capitalized);
        }
        candidates.add("get" + capitalized);
        candidates.add(name);
        for (String candidate : candidates) {
            try {
                Method method = shapeClass.getMethod(candidate);
                if (Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next generated naming convention.
            }
        }
        throw new UnsupportedSchemaException(
                "No direct getter for " + member.id() + " on " + shapeClass.getName());
    }

    private static Method resolvePresence(Class<?> shapeClass, Schema member) {
        String name = member.memberName();
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

    private static Method resolveSetter(Class<?> builderClass, Schema member, String unionAccessor) {
        String expected = unionAccessor == null ? toJavaName(member.memberName()) : unionAccessor;
        for (Method method : builderClass.getMethods()) {
            if (method.getName().equals(expected)
                    && method.getParameterCount() == 1
                    && Modifier.isPublic(method.getModifiers())) {
                return method;
            }
        }
        throw new UnsupportedSchemaException(
                "No direct builder setter for " + member.id() + " on " + builderClass.getName());
    }

    private static String toJavaName(String value) {
        if (value.indexOf('_') < 0 && value.indexOf('-') < 0 && value.indexOf(' ') < 0) {
            if (Character.isLowerCase(value.charAt(0))) {
                return value;
            }
            int lowercaseUntil = 1;
            while (lowercaseUntil < value.length() && Character.isUpperCase(value.charAt(lowercaseUntil))) {
                lowercaseUntil++;
            }
            int stop = lowercaseUntil > 1 && lowercaseUntil < value.length()
                    ? lowercaseUntil - 1
                    : lowercaseUntil;
            return value.substring(0, stop).toLowerCase(Locale.ROOT) + value.substring(stop);
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
            String jsonName,
            Method getter,
            Method presence,
            Method setter,
            Class<?> unionVariant,
            Method unionAccessor,
            boolean required,
            int estimatedBytecode) {
        public String wireName(boolean useJsonName) {
            return useJsonName && jsonName != null ? jsonName : memberName;
        }
    }
}
