/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt.plan;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Analyzes a Smithy struct schema and its Java class to produce a plan for code generation.
 * Resolves getter methods, classifies field types, and computes optimal serialization ordering.
 */
public final class StructCodePlan {

    private final Schema schema;
    private final Class<?> shapeClass;
    private final List<FieldPlan> fields;
    private final List<FieldPlan> serializationOrder;
    private final boolean isUnion;

    private StructCodePlan(Schema schema, Class<?> shapeClass, List<FieldPlan> fields, boolean isUnion) {
        this.schema = schema;
        this.shapeClass = shapeClass;
        this.fields = fields;
        this.isUnion = isUnion;

        this.serializationOrder = new ArrayList<>(fields);
        this.serializationOrder.sort(Comparator.<FieldPlan, Integer>comparing(f -> {
            if (f.required() && f.category().isFixedSize()) {
                return 0;
            }
            if (f.required()) {
                return 1;
            }
            return 2;
        }).thenComparingInt(FieldPlan::memberIndex));
    }

    public static StructCodePlan analyze(Schema schema, Class<?> shapeClass) {
        boolean isUnion = schema.type() == ShapeType.UNION;
        List<FieldPlan> fields = new ArrayList<>();

        for (Schema member : schema.members()) {
            Schema target = member.memberTarget();
            String memberName = member.memberName();
            String getterName = resolveGetter(shapeClass, memberName, target.type(), isUnion);
            FieldCategory category = classify(target);
            boolean required = member.hasTrait(TraitKey.REQUIRED_TRAIT);
            boolean nullable = !required || isUnion;

            String timestampFormat = null;
            var tsFmt = member.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
            if (tsFmt != null) {
                timestampFormat = tsFmt.getFormat().name();
            }

            boolean sparse = member.hasTrait(TraitKey.SPARSE_TRAIT)
                    || (target.type() == ShapeType.LIST && target.hasTrait(TraitKey.SPARSE_TRAIT))
                    || (target.type() == ShapeType.MAP && target.hasTrait(TraitKey.SPARSE_TRAIT));

            Class<?> elementClass = null;
            Class<?> mapValueClass = null;
            if (target.type() == ShapeType.LIST || target.type() == ShapeType.MAP) {
                try {
                    Method getter = shapeClass.getMethod(getterName);
                    Type returnType = getter.getGenericReturnType();
                    if (returnType instanceof ParameterizedType pt) {
                        Type[] args = pt.getActualTypeArguments();
                        if (target.type() == ShapeType.LIST && args.length >= 1) {
                            elementClass = toRawClass(args[0]);
                        } else if (target.type() == ShapeType.MAP && args.length >= 2) {
                            mapValueClass = toRawClass(args[1]);
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                    // fall through
                }
            }

            fields.add(new FieldPlan(
                    member,
                    memberName,
                    getterName,
                    member.memberIndex(),
                    category,
                    required,
                    nullable,
                    timestampFormat,
                    sparse,
                    elementClass,
                    mapValueClass));
        }

        return new StructCodePlan(schema, shapeClass, fields, isUnion);
    }

    private static String resolveGetter(Class<?> clazz, String memberName, ShapeType type, boolean isUnion) {
        String capitalized = Character.toUpperCase(memberName.charAt(0)) + memberName.substring(1);
        if (type == ShapeType.BOOLEAN) {
            try {
                clazz.getMethod("is" + capitalized);
                return "is" + capitalized;
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
        }
        try {
            clazz.getMethod("get" + capitalized);
            return "get" + capitalized;
        } catch (NoSuchMethodException ignored) {
            // fall through
        }
        try {
            clazz.getMethod(memberName);
            return memberName;
        } catch (NoSuchMethodException e) {
            if (isUnion) {
                // For unions, each variant is a separate record class. The getter name
                // is the member name (record component accessor) on the variant class,
                // not on the union interface. Return the member name as the getter since
                // the serializer dispatches via instanceof to the correct variant class.
                return memberName;
            }
            throw new RuntimeException("No getter found for " + memberName + " on " + clazz.getName(), e);
        }
    }

    private static FieldCategory classify(Schema target) {
        return switch (target.type()) {
            case BOOLEAN -> FieldCategory.BOOLEAN;
            case BYTE -> FieldCategory.BYTE;
            case SHORT -> FieldCategory.SHORT;
            case INTEGER -> FieldCategory.INTEGER;
            case LONG -> FieldCategory.LONG;
            case FLOAT -> FieldCategory.FLOAT;
            case DOUBLE -> FieldCategory.DOUBLE;
            case STRING -> FieldCategory.STRING;
            case BLOB -> FieldCategory.BLOB;
            case TIMESTAMP -> FieldCategory.TIMESTAMP;
            case BIG_INTEGER -> FieldCategory.BIG_INTEGER;
            case BIG_DECIMAL -> FieldCategory.BIG_DECIMAL;
            case ENUM -> FieldCategory.ENUM_STRING;
            case INT_ENUM -> FieldCategory.INT_ENUM;
            case LIST -> FieldCategory.LIST;
            case MAP -> FieldCategory.MAP;
            case STRUCTURE -> FieldCategory.STRUCT;
            case UNION -> FieldCategory.UNION;
            case DOCUMENT -> FieldCategory.DOCUMENT;
            default -> throw new IllegalArgumentException("Unsupported shape type: " + target.type());
        };
    }

    private static Class<?> toRawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        return Object.class;
    }

    public Schema schema() {
        return schema;
    }

    public Class<?> shapeClass() {
        return shapeClass;
    }

    public List<FieldPlan> fields() {
        return fields;
    }

    public List<FieldPlan> serializationOrder() {
        return serializationOrder;
    }

    public boolean isUnion() {
        return isUnion;
    }
}
