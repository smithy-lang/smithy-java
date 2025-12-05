package software.amazon.smithy.java.jmespath;

import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathException;
import software.amazon.smithy.jmespath.JmespathExceptionType;
import software.amazon.smithy.jmespath.RuntimeType;
import software.amazon.smithy.jmespath.evaluation.EvaluationUtils;
import software.amazon.smithy.jmespath.evaluation.JmespathRuntime;
import software.amazon.smithy.jmespath.evaluation.ListArrayBuilder;
import software.amazon.smithy.jmespath.evaluation.MapObjectBuilder;
import software.amazon.smithy.jmespath.evaluation.NumberType;
import software.amazon.smithy.jmespath.evaluation.WrappingIterable;
import software.amazon.smithy.model.shapes.ShapeType;

import java.math.BigDecimal;
import java.math.BigInteger;

// TODO: default equal isn't correct, need to normalize number types
public class DocumentJmespathRuntime implements JmespathRuntime<Document> {

    public static final DocumentJmespathRuntime INSTANCE = new DocumentJmespathRuntime();

    @Override
    public RuntimeType typeOf(Document document) {
        if (document == null) {
            return RuntimeType.NULL;
        }
        return switch (document.type()) {
            case BOOLEAN ->
                RuntimeType.BOOLEAN;
            case BLOB, ENUM, STRING ->
                RuntimeType.STRING;
            case BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE, BIG_DECIMAL, BIG_INTEGER, TIMESTAMP ->
                RuntimeType.NUMBER;
            case LIST, SET ->
                RuntimeType.ARRAY;
            case MAP, STRUCTURE, UNION ->
                RuntimeType.OBJECT;
            default -> throw new IllegalArgumentException("Unknown runtime type: " + document.type());
        };
    }

    @Override
    public Document createNull() {
        return null;
    }

    @Override
    public Document createBoolean(boolean b) {
        return Document.of(b);
    }

    @Override
    public boolean asBoolean(Document document) {
        return document.asBoolean();
    }

    @Override
    public Document createString(String s) {
        return Document.of(s);
    }

    @Override
    public String asString(Document document) {
        if (document.isType(ShapeType.BLOB)) {
            return JMESPathDocumentUtils.asString(document.asBlob());
        } else {
            try {
                return document.asString();
            } catch (SerializationException e) {
                throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Not a string: " + document, e);
            }
        }
    }

    @Override
    public Document createNumber(Number number) {
        return switch (EvaluationUtils.numberType(number)) {
            case BYTE -> Document.of(number.byteValue());
            case SHORT -> Document.of(number.shortValue());
            case INTEGER -> Document.of(number.intValue());
            case LONG -> Document.of(number.longValue());
            case FLOAT -> Document.of(number.floatValue());
            case DOUBLE -> Document.of(number.doubleValue());
            case BIG_INTEGER -> Document.of((BigInteger)number);
            case BIG_DECIMAL -> Document.of((BigDecimal)number);
        };
    }

    @Override
    public NumberType numberType(Document document) {
        return switch (document.type()) {
            case BYTE -> NumberType.BYTE;
            case SHORT -> NumberType.SHORT;
            case INTEGER, INT_ENUM -> NumberType.INTEGER;
            case LONG -> NumberType.LONG;
            case FLOAT -> NumberType.FLOAT;
            case DOUBLE -> NumberType.DOUBLE;
            case BIG_DECIMAL, TIMESTAMP -> NumberType.BIG_DECIMAL;
            case BIG_INTEGER -> NumberType.BIG_INTEGER;
            default -> throw new IllegalArgumentException("Not a number: " + document);
        };
    }

    @Override
    public Number asNumber(Document document) {
        if (document.isType(ShapeType.TIMESTAMP)) {
            return JMESPathDocumentUtils.asBigDecimal(document.asTimestamp());
        } else {
            try {
                return document.asNumber();
            } catch (SerializationException e) {
                throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Not a number: " + document, e);
            }
        }
    }

    @Override
    public Number length(Document document) {
        if (is(document, RuntimeType.STRING)) {
            return EvaluationUtils.codePointCount(document.asString());
        } else {
            // This handles objects and arrays
            return document.size();
        }
    }

    @Override
    public Document element(Document document, Document index) {
        return document.asList().get(asNumber(index).intValue());
    }

    @Override
    public Iterable<Document> toIterable(Document document) {
        return switch (typeOf(document)) {
            case ARRAY -> document.asList();
            case OBJECT -> new WrappingIterable<>(Document::of, document.asStringMap().keySet());
            default -> throw new IllegalArgumentException("Not iterable: " + document);
        };
    }

    @Override
    public JmespathRuntime.ArrayBuilder<Document> arrayBuilder() {
        return new ListArrayBuilder<>(this, Document::of);
    }

    @Override
    public Document value(Document document, Document key) {
        if (typeOf(document) == RuntimeType.OBJECT) {
            return document.getMember(key.asString());
        } else {
            return createNull();
        }
    }

    @Override
    public JmespathRuntime.ObjectBuilder<Document> objectBuilder() {
        return new MapObjectBuilder<>(this, Document::of);
    }
}
