/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.jmespath.JmespathException;
import software.amazon.smithy.jmespath.JmespathExceptionType;
import software.amazon.smithy.jmespath.RuntimeType;
import software.amazon.smithy.jmespath.evaluation.EvaluationUtils;
import software.amazon.smithy.jmespath.evaluation.JmespathRuntime;
import software.amazon.smithy.jmespath.evaluation.ListArrayBuilder;
import software.amazon.smithy.jmespath.evaluation.MapObjectBuilder;
import software.amazon.smithy.jmespath.evaluation.MappingIterable;
import software.amazon.smithy.jmespath.evaluation.NumberType;

final class GeneratedTypeJmespathRuntime implements JmespathRuntime<Object> {

    public static final GeneratedTypeJmespathRuntime INSTANCE = new GeneratedTypeJmespathRuntime();

    private static RuntimeType resolveType(Object value) {
        return switch (value) {
            case String ignored -> RuntimeType.STRING;
            case SmithyEnum ignored -> RuntimeType.STRING;
            case Boolean ignored -> RuntimeType.BOOLEAN;
            case Number ignored -> RuntimeType.NUMBER;
            case Instant ignored -> RuntimeType.NUMBER;
            case SmithyIntEnum ignored -> RuntimeType.NUMBER;
            case List<?> ignored -> RuntimeType.ARRAY;
            case SerializableStruct ignored -> RuntimeType.OBJECT;
            case Map<?, ?> ignored -> RuntimeType.OBJECT;
            default -> null;
        };
    }

    private static NumberType resolveNumberType(Object value) {
        return switch (value) {
            case Byte ignored -> NumberType.BYTE;
            case Short ignored -> NumberType.SHORT;
            case Integer ignored -> NumberType.INTEGER;
            case Long ignored -> NumberType.LONG;
            case Float ignored -> NumberType.FLOAT;
            case Double ignored -> NumberType.DOUBLE;
            case BigInteger ignored -> NumberType.BIG_INTEGER;
            case BigDecimal ignored -> NumberType.BIG_DECIMAL;
            case Instant ignored -> NumberType.BIG_DECIMAL;
            case SmithyIntEnum ignored -> NumberType.INTEGER;
            default -> null;
        };
    }

    @Override
    public RuntimeType typeOf(Object value) {
        if (value == null) {
            return RuntimeType.NULL;
        }

        RuntimeType runtimeType = resolveType(value);
        if (runtimeType != null) {
            return runtimeType;
        }

        throw new IllegalArgumentException();
    }

    @Override
    public Object createNull() {
        return null;
    }

    @Override
    public Object createBoolean(boolean b) {
        return b;
    }

    @Override
    public boolean asBoolean(Object value) {
        return (Boolean) value;
    }

    @Override
    public Object createString(String s) {
        return s;
    }

    @Override
    public String asString(Object value) {
        return switch (value) {
            case SmithyEnum enumValue -> enumValue.getValue();
            case String s -> s;
            default -> {
                throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
            }
        };
    }

    @Override
    public Object createNumber(Number number) {
        return number;
    }

    @Override
    public NumberType numberType(Object value) {
        NumberType numberType = resolveNumberType(value);
        if (numberType != null) {
            return numberType;
        }
        throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
    }

    @Override
    public Number asNumber(Object value) {
        return switch (value) {
            case Number number -> number;
            case Instant instant -> JmesPathDocumentUtils.asBigDecimal(instant);
            case SmithyIntEnum smithyIntEnum -> smithyIntEnum.getValue();
            case null, default -> {
                throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
            }
        };
    }

    @Override
    public int length(Object value) {
        return switch (typeOf(value)) {
            case STRING -> EvaluationUtils.codePointCount(asString(value)); // handle enums and strings
            case ARRAY -> ((List<?>) value).size();
            case OBJECT -> {
                if (value instanceof Map<?, ?>) {
                    yield ((Map<?, ?>) value).size();
                } else {
                    yield ((SerializableStruct) value).schema().members().size();
                }
            }
            default ->
                throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
        };
    }

    @Override
    public Object element(Object value, int index) {
        return ((List<?>) value).get(index);
    }

    @Override
    public Iterable<?> asIterable(Object value) {
        return switch (value) {
            case List<?> list -> list;
            case Map<?, ?> map -> map.keySet();
            case SerializableStruct struct -> new MappingIterable<>(Schema::memberName, struct.schema().members());
            case null, default -> {
                throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
            }
        };
    }

    @Override
    public ArrayBuilder<Object> arrayBuilder() {
        return new ListArrayBuilder<>(this, x -> x);
    }

    @Override
    public Object value(Object object, Object key) {
        if (object instanceof SerializableStruct struct) {
            var member = struct.schema().member((String) key);
            return member != null ? struct.getMemberValue(member) : null;
        } else if (object instanceof Map<?, ?> map) {
            return map.get(key);
        } else {
            return null;
        }
    }

    @Override
    public ObjectBuilder<Object> objectBuilder() {
        return new MapObjectBuilder<>(this, x -> x);
    }
}
