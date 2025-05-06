/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.ast.ExpressionTypeExpression;
import software.amazon.smithy.jmespath.ast.FunctionExpression;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Built-in JMESPath functions.
 *
 * @see <a href="https://jmespath.org/specification.html#built-in-functions">JMESPath built-in functions</a>
 */
// TODO: Complete support for all built-in functions
enum JMESPathFunction {
    ABS("abs", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case BYTE -> Document.of(Math.abs(arg.asByte()));
                case INTEGER, INT_ENUM -> Document.of(Math.abs(arg.asInteger()));
                case LONG -> Document.of(Math.abs(arg.asLong()));
                case BIG_DECIMAL -> Document.of(arg.asBigDecimal().abs());
                case BIG_INTEGER -> Document.of(arg.asBigInteger().abs());
                case SHORT -> Document.of(Math.abs(arg.asShort()));
                case DOUBLE -> Document.of(Math.abs(arg.asDouble()));
                case FLOAT -> Document.of(Math.abs(arg.asFloat()));
                default -> throw new IllegalArgumentException("`abs` only supports numeric arguments");
            };
        }
    },
    AVG("avg", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var list = arguments.get(0);
            if (!list.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`avg` only supports array arguments");
            }
            if (list.size() == 0) {
                return null;
            }
            var firstItem = list.asList().get(0);
            return switch (firstItem.type()) {
                case INTEGER, INT_ENUM, LONG, SHORT, BYTE -> {
                    long sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asLong();
                    }
                    yield Document.of((double) sum / (double) list.size());
                }
                case FLOAT, DOUBLE -> {
                    double sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asDouble();
                    }
                    yield Document.of(sum / (double) list.size());
                }
                case BIG_DECIMAL -> {
                    var sum = BigDecimal.valueOf(0);
                    for (var item : list.asList()) {
                        sum = sum.add(item.asBigDecimal());
                    }
                    yield Document.of(sum.divide(BigDecimal.valueOf(list.size()), RoundingMode.HALF_UP));
                }
                case BIG_INTEGER -> {
                    var sum = BigInteger.valueOf(0);
                    for (var item : list.asList()) {
                        sum = sum.add(item.asBigInteger());
                    }
                    yield Document.of(sum.divide(BigInteger.valueOf(list.size())));
                }
                default -> null;
            };
        }
    },
    CONTAINS("contains", 2) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression ignored) {
            var subject = arguments.get(0);
            var search = arguments.get(1);
            return switch (subject.type()) {
                case STRING -> {
                    var searchString = search.asString();
                    yield Document.of(subject.asString().contains(searchString));
                }
                case LIST -> {
                    var subjectList = subject.asList();
                    for (var item : subjectList) {
                        if (item.equals(search)) {
                            yield Document.of(true);
                        }
                    }
                    yield Document.of(false);
                }
                default -> throw new IllegalArgumentException("`contains` only supports lists or strings as subject");
            };
        }
    },
    CEIL("ceil", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case BYTE, INTEGER, INT_ENUM, BIG_INTEGER, LONG, SHORT -> arg;
                case BIG_DECIMAL -> Document.of(arg.asBigDecimal().setScale(0, RoundingMode.CEILING));
                case DOUBLE -> Document.of((long) Math.ceil(arg.asDouble()));
                case FLOAT -> Document.of((long) Math.ceil(arg.asFloat()));
                // Non numeric searches return null per spec
                default -> null;
            };
        }
    },
    ENDS_WITH("ends_with", 2) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            var search = arguments.get(1);
            if (!subject.type().equals(ShapeType.STRING) || !search.type().equals(ShapeType.STRING)) {
                throw new IllegalArgumentException("`ends_with` only supports string arguments.");
            }
            return Document.of(subject.asString().endsWith(search.asString()));
        }
    },
    FLOOR("floor", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case BYTE, INTEGER, INT_ENUM, BIG_INTEGER, LONG, SHORT -> arg;
                case BIG_DECIMAL -> Document.of(arg.asBigDecimal().setScale(0, RoundingMode.FLOOR));
                case DOUBLE -> Document.ofNumber((long) Math.floor(arg.asDouble()));
                case FLOAT -> Document.ofNumber((long) Math.floor(arg.asFloat()));
                // Non numeric searches return null per specification
                default -> null;
            };
        }
    },
    JOIN("join", 2) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var delimiter = arguments.get(0);
            if (!delimiter.type().equals(ShapeType.STRING)) {
                throw new IllegalArgumentException("`join` delimiter must be a string.");
            }
            var list = arguments.get(1);
            if (!list.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`join` only supports array joining.");
            }
            var builder = new StringBuilder();
            var iter = list.asList().iterator();
            while (iter.hasNext()) {
                builder.append(iter.next().asString());
                if (iter.hasNext()) {
                    builder.append(delimiter.asString());
                }
            }
            return Document.of(builder.toString());
        }
    },
    KEYS("keys", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case MAP, STRUCTURE -> {
                    List<Document> keys = arg.getMemberNames().stream().map(Document::of).toList();
                    yield Document.of(keys);
                }
                default -> throw new IllegalArgumentException("`keys` only supports object arguments");
            };
        }
    },
    LENGTH("length", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case STRING -> Document.of((long) arg.asString().length());
                case MAP, STRUCTURE, LIST -> Document.of((long) arg.size());
                default -> throw new IllegalArgumentException("Type: " + arg.type() + " not supported by `length`");
            };
        }
    },
    MAP("map", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            return null;
        }
    },
    MAX_BY("max_by", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`max_by` only supports arrays");
            }
            if (subject.size() == 0) {
                return null;
            }
            Document max = null;
            Document maxValue = null;
            for (var item : subject.asList()) {
                var value = JMESPathDocumentQuery.query(fnRef, item);
                if (max == null || Document.compare(maxValue, value) < 0) {
                    max = item;
                    maxValue = value;
                }
            }
            return max;
        }
    },
    MAX("max", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`max` only supports array arguments");
            }
            return subject.size() == 0 ? null : Collections.max(subject.asList(), Document::compare);
        }
    },
    MERGE("merge", 0) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            return null;
        }
    },
    MIN("min", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`max` only supports array arguments");
            }
            return subject.size() == 0 ? null : Collections.min(subject.asList(), Document::compare);
        }
    },
    MIN_BY("min_by", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`min_by` only supports arrays");
            }
            if (subject.size() == 0) {
                return null;
            }
            Document min = null;
            Document minValue = null;
            for (var item : subject.asList()) {
                var value = JMESPathDocumentQuery.query(fnRef, item);
                if (min == null || Document.compare(minValue, value) > 0) {
                    min = item;
                    minValue = value;
                }
            }
            return min;
        }
    },
    NOT_NULL("not_null", 0) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            for (var arg : arguments) {
                if (arg != null) {
                    return arg;
                }
            }
            return null;
        }
    },
    REVERSE("reverse", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            return switch (subject.type()) {
                case STRING -> Document.of(new StringBuffer(subject.asString()).reverse().toString());
                case LIST -> {
                    var copy = new ArrayList<>(subject.asList());
                    Collections.reverse(copy);
                    yield Document.of(copy);
                }
                default -> throw new IllegalArgumentException("`reverse` only supports array or string arguments");
            };
        }
    },
    SORT("sort", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`sort` only supports array arguments");
            }
            return Document.of(subject.asList().stream().sorted(Document::compare).toList());
        }
    },
    SORT_BY("sort_by", 1, true) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`sort_by` only supports arrays");
            }
            return Document.of(subject.asList()
                    .stream()
                    .sorted((l, r) -> Document.compare(JMESPathDocumentQuery.query(fnRef, l),
                            JMESPathDocumentQuery.query(fnRef, r)))
                    .toList());
        }
    },
    STARTS_WITH("starts_with", 2) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var subject = arguments.get(0);
            var search = arguments.get(1);
            if (!subject.type().equals(ShapeType.STRING) || !search.type().equals(ShapeType.STRING)) {
                throw new IllegalArgumentException("`starts_with` only supports string arguments.");
            }
            return Document.of(subject.asString().startsWith(search.asString()));
        }
    },
    SUM("sum", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var list = arguments.get(0);
            if (!list.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`avg` only supports array arguments");
            }
            if (list.size() == 0) {
                return Document.of(0L);
            }
            var firstItem = list.asList().get(0);
            return switch (firstItem.type()) {
                case BYTE -> {
                    byte sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asByte();
                    }
                    yield Document.of(sum);
                }
                case SHORT -> {
                    short sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asShort();
                    }
                    yield Document.of(sum);
                }
                case INTEGER, INT_ENUM -> {
                    int sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asInteger();
                    }
                    yield Document.of(sum);
                }
                case LONG -> {
                    long sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asLong();
                    }
                    yield Document.of(sum);
                }
                case FLOAT -> {
                    float sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asFloat();
                    }
                    yield Document.of(sum);
                }
                case DOUBLE -> {
                    double sum = 0;
                    for (var item : list.asList()) {
                        sum += item.asDouble();
                    }
                    yield Document.of(sum);
                }
                case BIG_DECIMAL -> {
                    var sum = BigDecimal.valueOf(0);
                    for (var item : list.asList()) {
                        sum = sum.add(item.asBigDecimal());
                    }
                    yield Document.of(sum);
                }
                case BIG_INTEGER -> {
                    var sum = BigInteger.valueOf(0);
                    for (var item : list.asList()) {
                        sum = sum.add(item.asBigInteger());
                    }
                    yield Document.of(sum);
                }
                default -> null;
            };
        }
    },
    TO_ARRAY("to_array", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            throw new UnsupportedOperationException("To Array function is not supported");
        }
    },
    TO_STRING("to_string", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            throw new UnsupportedOperationException("to_string function is not supported");
        }
    },
    TO_NUMBER("to_number", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var argument = arguments.get(0);
            if (argument == null) {
                return null;
            }
            return switch (argument.type()) {
                case STRING -> {
                    try {
                        yield Document.ofNumber(new BigDecimal(argument.asString()));
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
                case SHORT, BYTE, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE, BIG_DECIMAL, BIG_INTEGER ->
                    Document.ofNumber(argument.asNumber());
                default -> null;
            };
        }
    },
    TYPE("type", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var argument = arguments.get(0);
            if (argument == null) {
                return Document.of("null");
            }
            String typeStr = switch (argument.type()) {
                case DOUBLE, INTEGER, INT_ENUM, LONG, SHORT, BYTE, BIG_DECIMAL, BIG_INTEGER, FLOAT -> "number";
                case STRING, ENUM -> "string";
                case MAP, STRUCTURE, UNION -> "object";
                case BOOLEAN -> "boolean";
                case LIST -> "array";
                default -> throw new IllegalArgumentException("unsupported smithy type: " + argument.type());
            };
            return Document.of(typeStr);
        }
    },
    VALUES("values", 1) {
        @Override
        protected Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case MAP, STRUCTURE -> {
                    List<Document> values = arg.asStringMap().values().stream().map(Document::of).toList();
                    yield Document.of(values);
                }
                default -> throw new IllegalArgumentException("`values` only supports object arguments");
            };
        }
    };

    private final String name;
    private final int argumentCount;
    private final boolean expectsFnRef;

    JMESPathFunction(String name, int argumentCount) {
        this(name, argumentCount, false);
    }

    JMESPathFunction(String name, int argumentCount, boolean expectsFnRef) {
        this.name = name;
        this.argumentCount = argumentCount;
        this.expectsFnRef = expectsFnRef;
    }

    static JMESPathFunction from(FunctionExpression expression) {
        var name = expression.getName();
        for (JMESPathFunction val : JMESPathFunction.values()) {
            if (val.name.equalsIgnoreCase(name)) {
                return val;
            }
        }
        throw new UnsupportedOperationException("Could not find function implementation for " + name);
    }

    /**
     * Apply the JMESPath function to a set of arguments.
     * @param arguments arguments
     * @param fnRef function reference if supported by function, or null.
     * @return result of function
     */
    public Document apply(List<Document> arguments, ExpressionTypeExpression fnRef) {
        if (argumentCount > 0 && argumentCount != arguments.size()) {
            throw new IllegalArgumentException("Unexpected number of arguments. Expected " + argumentCount
                    + " but found " + arguments.size());
        }
        if (expectsFnRef && fnRef == null) {
            throw new IllegalArgumentException("Expected a function reference for `" + name + "`, but found null.");
        }
        return applyImpl(arguments, fnRef);
    }

    protected abstract Document applyImpl(List<Document> arguments, ExpressionTypeExpression fnRef);
}
