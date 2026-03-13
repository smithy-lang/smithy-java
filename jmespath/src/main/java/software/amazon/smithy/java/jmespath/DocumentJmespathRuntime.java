/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.jmespath.JmespathException;
import software.amazon.smithy.jmespath.JmespathExceptionType;
import software.amazon.smithy.jmespath.RuntimeType;
import software.amazon.smithy.jmespath.evaluation.EvaluationUtils;
import software.amazon.smithy.jmespath.evaluation.JmespathRuntime;
import software.amazon.smithy.jmespath.evaluation.ListArrayBuilder;
import software.amazon.smithy.jmespath.evaluation.MapObjectBuilder;
import software.amazon.smithy.jmespath.evaluation.MappingIterable;
import software.amazon.smithy.jmespath.evaluation.NumberType;
import software.amazon.smithy.model.shapes.ShapeType;

final class DocumentJmespathRuntime implements JmespathRuntime<Document> {

    public static final DocumentJmespathRuntime INSTANCE = new DocumentJmespathRuntime();

    @Override
    public RuntimeType typeOf(Document document) {
        if (document == null) {
            return RuntimeType.NULL;
        }
        return switch (document.type()) {
            case BOOLEAN -> RuntimeType.BOOLEAN;
            case BLOB, ENUM, STRING -> RuntimeType.STRING;
            case BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT,
                    DOUBLE, BIG_DECIMAL, BIG_INTEGER, TIMESTAMP ->
                RuntimeType.NUMBER;
            case LIST, SET -> RuntimeType.ARRAY;
            case MAP, STRUCTURE, UNION -> RuntimeType.OBJECT;
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
            return ByteBufferUtils.base64Encode(document.asBlob());
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
        return Document.ofNumber(number);
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
            return JmesPathDocumentUtils.asBigDecimal(document.asTimestamp());
        } else {
            try {
                return document.asNumber();
            } catch (SerializationException e) {
                throw new JmespathException(JmespathExceptionType.INVALID_TYPE, "Not a number: " + document, e);
            }
        }
    }

    @Override
    public int length(Document document) {
        if (is(document, RuntimeType.STRING)) {
            return EvaluationUtils.codePointCount(document.asString());
        } else {
            // This handles objects and arrays
            return document.size();
        }
    }

    @Override
    public Document element(Document document, int index) {
        return document.asList().get(index);
    }

    @Override
    public Iterable<Document> asIterable(Document document) {
        return switch (typeOf(document)) {
            case ARRAY -> document.asList();
            case OBJECT -> new MappingIterable<>(Document::of, document.asStringMap().keySet());
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
