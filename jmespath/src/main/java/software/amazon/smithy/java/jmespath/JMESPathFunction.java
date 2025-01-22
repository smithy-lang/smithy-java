/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.java.core.serde.document.Document;
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
        public Document applyImpl(List<Document> arguments) {
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
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("Avg function is not supported");
        }
    },
    CONTAINS("contains", 2) {
        @Override
        public Document applyImpl(List<Document> arguments) {
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
        public Document applyImpl(List<Document> arguments) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case BYTE, INTEGER, INT_ENUM, BIG_INTEGER, LONG, SHORT -> arg;
                case BIG_DECIMAL -> Document.of(arg.asBigDecimal().setScale(0, RoundingMode.CEILING));
                case DOUBLE -> Document.of(Math.ceil(arg.asDouble()));
                case FLOAT -> Document.of(Math.ceil(arg.asFloat()));
                // Non numeric searches return null per spec
                default -> null;
            };
        }
    },
    ENDS_WITH("ends_with", 2) {
        @Override
        public Document applyImpl(List<Document> arguments) {
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
        public Document applyImpl(List<Document> arguments) {
            var arg = arguments.get(0);
            return switch (arg.type()) {
                case BYTE, INTEGER, INT_ENUM, BIG_INTEGER, LONG, SHORT -> arg;
                case BIG_DECIMAL -> Document.of(arg.asBigDecimal().setScale(0, RoundingMode.FLOOR));
                case DOUBLE -> Document.of(Math.floor(arg.asDouble()));
                case FLOAT -> Document.of(Math.floor(arg.asFloat()));
                // Non numeric searches return null per spec
                default -> null;
            };
        }
    },
    JOIN("join", 2) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            return null;
        }
    },
    KEYS("keys", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
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
        public Document applyImpl(List<Document> arguments) {
            var arg = arguments.get(0);
            return Document.of(arg.size());
        }
    },
    MAP("map", 2) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("Map function is not supported");
        }
    },
    MAX_BY("max_by", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("Max by function is not supported");
        }
    },
    MAX("max", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`max` only supports array arguments");
            }
            return Collections.max(subject.asList(), Document::compare);
        }
    },
    MERGE("merge", 0) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("Merge function is not supported");
        }
    },
    MIN("min", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`max` only supports array arguments");
            }
            return Collections.min(subject.asList(), Document::compare);
        }
    },
    MIN_BY("min_by", 2) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("Min by function is not supported");
        }
    },
    NOT_NULL("not_null", 0) {
        @Override
        public Document applyImpl(List<Document> arguments) {
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
        public Document applyImpl(List<Document> arguments) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`max` only supports array arguments");
            }
            var copy = new ArrayList<>(subject.asList());
            Collections.reverse(copy);
            return Document.of(copy);
        }
    },
    SORT("sort", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            var subject = arguments.get(0);
            if (!subject.type().equals(ShapeType.LIST)) {
                throw new IllegalArgumentException("`max` only supports array arguments");
            }
            return Document.of(subject.asList().stream().sorted(Document::compare).toList());
        }
    },
    SORT_BY("sort_by", 2) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("Sort by function is not supported");
        }
    },
    STARTS_WITH("starts_with", 2) {
        @Override
        public Document applyImpl(List<Document> arguments) {
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
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("Sum function is not supported");
        }
    },
    TO_ARRAY("to_array", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("To Array function is not supported");
        }
    },
    TO_STRING("to_string", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("to_string function is not supported");
        }
    },
    TO_NUMBER("to_number", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
            throw new UnsupportedOperationException("to_number function is not supported");
        }
    },
    TYPE("type", 1) {
        @Override
        public Document applyImpl(List<Document> arguments) {
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
        public Document applyImpl(List<Document> arguments) {
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

    JMESPathFunction(String name, int argumentCount) {
        this.name = name;
        this.argumentCount = argumentCount;
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
     * @return result of function
     */
    public Document apply(List<Document> arguments) {
        if (argumentCount > 0 && argumentCount != arguments.size()) {
            throw new IllegalArgumentException("Unexpected number of arguments. Expected " + argumentCount
                    + " but found " + arguments.size());
        }
        return applyImpl(arguments);
    }

    protected abstract Document applyImpl(List<Document> arguments);
}
