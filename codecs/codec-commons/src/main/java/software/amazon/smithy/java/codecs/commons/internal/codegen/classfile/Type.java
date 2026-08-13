/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

import java.lang.reflect.Method;

/**
 * Descriptor utilities used while generating codec classes.
 */
public final class Type {
    public static final Type VOID_TYPE = new Type(void.class);

    private final Class<?> type;

    private Type(Class<?> type) {
        this.type = type;
    }

    public static String getInternalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    public static String getDescriptor(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) {
                return "V";
            } else if (type == boolean.class) {
                return "Z";
            } else if (type == byte.class) {
                return "B";
            } else if (type == char.class) {
                return "C";
            } else if (type == short.class) {
                return "S";
            } else if (type == int.class) {
                return "I";
            } else if (type == long.class) {
                return "J";
            } else if (type == float.class) {
                return "F";
            } else {
                return "D";
            }
        }
        return type.isArray() ? type.getName().replace('.', '/') : "L" + getInternalName(type) + ";";
    }

    public static String getMethodDescriptor(Method method) {
        Type[] parameters = new Type[method.getParameterCount()];
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getType(parameterTypes[i]);
        }
        return getMethodDescriptor(getType(method.getReturnType()), parameters);
    }

    public static String getMethodDescriptor(Type returnType, Type... parameterTypes) {
        StringBuilder result = new StringBuilder("(");
        for (Type parameter : parameterTypes) {
            result.append(getDescriptor(parameter.type));
        }
        return result.append(')').append(getDescriptor(returnType.type)).toString();
    }

    public static Type getType(Class<?> type) {
        return new Type(type);
    }

    public static boolean isPrimitive(Class<?> type) {
        return type.isPrimitive();
    }
}
