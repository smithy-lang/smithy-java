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
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathException;
import software.amazon.smithy.jmespath.JmespathExceptionType;
import software.amazon.smithy.jmespath.RuntimeType;
import software.amazon.smithy.jmespath.evaluation.EvaluationUtils;
import software.amazon.smithy.jmespath.evaluation.InheritingClassMap;
import software.amazon.smithy.jmespath.evaluation.JmespathRuntime;
import software.amazon.smithy.jmespath.evaluation.ListArrayBuilder;
import software.amazon.smithy.jmespath.evaluation.MapObjectBuilder;
import software.amazon.smithy.jmespath.evaluation.MappingIterable;
import software.amazon.smithy.jmespath.evaluation.NumberType;

public class GeneratedTypeJmespathRuntime implements JmespathRuntime<Object> {

    public static final GeneratedTypeJmespathRuntime INSTANCE = new GeneratedTypeJmespathRuntime();

    private static final InheritingClassMap<RuntimeType> typeForClass = InheritingClassMap.<RuntimeType>builder()
            .put(String.class, RuntimeType.STRING)
            .put(SmithyEnum.class, RuntimeType.STRING)
            .put(Boolean.class, RuntimeType.BOOLEAN)
            .put(Byte.class, RuntimeType.NUMBER)
            .put(Short.class, RuntimeType.NUMBER)
            .put(Integer.class, RuntimeType.NUMBER)
            .put(Long.class, RuntimeType.NUMBER)
            .put(Float.class, RuntimeType.NUMBER)
            .put(Double.class, RuntimeType.NUMBER)
            .put(BigInteger.class, RuntimeType.NUMBER)
            .put(BigDecimal.class, RuntimeType.NUMBER)
            .put(Instant.class, RuntimeType.NUMBER)
            .put(SmithyIntEnum.class, RuntimeType.NUMBER)
            .put(List.class, RuntimeType.ARRAY)
            .put(SerializableStruct.class, RuntimeType.OBJECT)
            .put(Map.class, RuntimeType.OBJECT)
            .build();

    private static final InheritingClassMap<NumberType> numberTypeForClass = InheritingClassMap.<NumberType>builder()
            .put(Byte.class, NumberType.BYTE)
            .put(Short.class, NumberType.SHORT)
            .put(Integer.class, NumberType.INTEGER)
            .put(Long.class, NumberType.LONG)
            .put(Float.class, NumberType.FLOAT)
            .put(Double.class, NumberType.DOUBLE)
            .put(BigInteger.class, NumberType.BIG_INTEGER)
            .put(BigDecimal.class, NumberType.BIG_DECIMAL)
            .put(Instant.class, NumberType.BIG_DECIMAL)
            .put(SmithyIntEnum.class, NumberType.INTEGER)
            .build();

    @Override
    public RuntimeType typeOf(Object value) {
        if (value == null) {
            return RuntimeType.NULL;
        }

        RuntimeType runtimeType = typeForClass.get(value.getClass());
        if (runtimeType != null) {
            return runtimeType;
        }

        throw new IllegalArgumentException();
    }

    @Override
    public Document createNull() {
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
        if (value instanceof SmithyEnum enumValue) {
            return enumValue.getValue();
        } else if (value instanceof String s) {
            return s;
        } else {
            throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
        }
    }

    @Override
    public Object createNumber(Number number) {
        return number;
    }

    @Override
    public NumberType numberType(Object value) {
        NumberType numberType = numberTypeForClass.get(value.getClass());
        if (numberType != null) {
            return numberType;
        }
        throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
    }

    @Override
    public Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        } else if (value instanceof Instant instant) {
            return JMESPathDocumentUtils.asBigDecimal(instant);
        } else if (value instanceof SmithyIntEnum) {
            return ((SmithyIntEnum) value).getValue();
        } else {
            throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
        }
    }

    @Override
    public Number length(Object value) {
        return switch (typeOf(value)) {
            case STRING -> EvaluationUtils.codePointCount((String) value);
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
    public Object element(Object value, Object index) {
        return ((List<?>) value).get(asNumber(index).intValue());
    }

    @Override
    public Iterable<?> asIterable(Object value) {
        if (value instanceof List<?> list) {
            return list;
        } else if (value instanceof Map<?, ?> map) {
            return map.keySet();
        } else if (value instanceof SerializableStruct struct) {
            return new MappingIterable<>(Schema::memberName, struct.schema().members());
        } else {
            throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Incorrect runtime type: " + value);
        }
    }

    @Override
    public ArrayBuilder<Object> arrayBuilder() {
        return new ListArrayBuilder<>(this, x -> x);
    }

    @Override
    public Object value(Object object, Object key) {
        if (object instanceof SerializableStruct struct) {
            // TODO: Check what happens on invalid member name
            return struct.getMemberValue(struct.schema().member((String) key));
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
