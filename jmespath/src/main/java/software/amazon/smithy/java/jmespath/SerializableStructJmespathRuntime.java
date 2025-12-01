package software.amazon.smithy.java.jmespath;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.RuntimeType;
import software.amazon.smithy.jmespath.evaluation.EvaluationUtils;
import software.amazon.smithy.jmespath.evaluation.JmespathRuntime;
import software.amazon.smithy.jmespath.evaluation.ListArrayBuilder;
import software.amazon.smithy.jmespath.evaluation.MapObjectBuilder;
import software.amazon.smithy.jmespath.evaluation.NumberType;
import software.amazon.smithy.jmespath.evaluation.WrappingIterable;
import software.amazon.smithy.model.shapes.ShapeType;

import java.lang.invoke.SerializedLambda;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// TODO: still need to handle enums/intenum.
// Requires tracking schema values from containers since the generated types
// have no common supertype/or interface.
public class SerializableStructJmespathRuntime implements JmespathRuntime<Object> {

    // Exact class matches for faster typeOf matching
    private static final Map<Class<?>, RuntimeType> typeForClass = new HashMap<>();
    static {
        typeForClass.put(Boolean.class, RuntimeType.BOOLEAN);
        typeForClass.put(String.class, RuntimeType.STRING);
        for (Class<?> klass : EvaluationUtils.numberTypeForClass.keySet()) {
            typeForClass.put(klass, RuntimeType.NUMBER);
        }
        typeForClass.put(Instant.class, RuntimeType.NUMBER);
    }

    @Override
    public RuntimeType typeOf(Object value) {
        if (value == null) {
            return RuntimeType.NULL;
        }

        // Fast path: known exact class
        RuntimeType runtimeType = typeForClass.get(value.getClass());
        if (runtimeType != null) {
            return runtimeType;
        }

        // Slower instanceof checks
        // These could be cached and/or precalculated as well
        if (value instanceof List<?>) {
            return RuntimeType.ARRAY;
        } else if (value instanceof Map<?, ?> || value instanceof SerializableStruct) {
            return RuntimeType.OBJECT;
        } else {
            throw new IllegalArgumentException();
        }
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
    public boolean toBoolean(Object value) {
        return (Boolean)value;
    }

    @Override
    public Object createString(String s) {
        return s;
    }

    @Override
    public String toString(Object value) {
        return (String)value;
    }

    @Override
    public Object createNumber(Number number) {
        return number;
    }

    @Override
    public NumberType numberType(Object object) {
        return EvaluationUtils.numberType((Number)object);
    }

    @Override
    public Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        } else if (value instanceof Instant instant) {
            return JMESPathDocumentUtils.asBigDecimal(instant);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public Number length(Object value) {
        if (value instanceof String s) {
            return EvaluationUtils.codePointCount(s);
        } else if (value instanceof Collection<?> c) {
            return c.size();
        } else if (value instanceof SerializableStruct struct) {
            return struct.schema().members().size();
        } else {
            throw new IllegalArgumentException("Unknown runtime type: " + value);
        }
    }

    @Override
    public Object element(Object value, Object index) {
        return ((List<?>)value).get(toNumber(index).intValue());
    }

    @Override
    public Iterable<?> toIterable(Object value) {
        if (value instanceof List<?> list) {
            return list;
        } else if (value instanceof Map<?, ?> map) {
            return map.keySet();
        } else if (value instanceof SerializableStruct struct) {
            return new WrappingIterable<>(Schema::memberName, struct.schema().members());
        } else {
            throw new IllegalArgumentException("Unknown runtime type: " + value);
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
            return struct.getMemberValue(struct.schema().member((String)key));
        } else if (object instanceof Map<?, ?> map) {
            return map.get(key);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public ObjectBuilder<Object> objectBuilder() {
        return new MapObjectBuilder<>(this, x -> x);
    }
}
