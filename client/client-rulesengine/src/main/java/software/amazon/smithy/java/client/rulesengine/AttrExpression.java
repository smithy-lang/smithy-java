/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.net.URI;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.GetAttr;

/**
 * Implements the getAttr function by extracting paths from objects.
 */
sealed interface AttrExpression {

    Object apply(Object o);

    static AttrExpression from(GetAttr getAttr) {
        var path = getAttr.getPath();

        // Set the toString value on the final result.
        String str = getAttr.toString(); // in the form of something#path
        int position = str.lastIndexOf('#');
        var tostringValue = str.substring(position + 1);

        if (path.isEmpty()) {
            throw new UnsupportedOperationException("Invalid getAttr expression: requires at least one part");
        } else if (path.size() == 1) {
            return new ToString(tostringValue, from(getAttr.getPath().get(0)));
        }

        // Parse the multi-level expression ("foo.bar.baz[9]").
        var result = new AndThen(from(path.get(0)), from(path.get(1)));
        for (var i = 2; i < path.size(); i++) {
            result = new AndThen(result, from(path.get(i)));
        }

        return new ToString(tostringValue, result);
    }

    private static AttrExpression from(GetAttr.Part part) {
        if (part instanceof GetAttr.Part.Key k) {
            return new GetKey(k.key().toString());
        } else if (part instanceof GetAttr.Part.Index i) {
            return new GetIndex(i.index());
        } else {
            throw new UnsupportedOperationException("Unexpected GetAttr part: " + part);
        }
    }

    /**
     * Creates an AttrExpression from a string. Generally used when loading from pre-compiled programs.
     *
     * @param value Value to parse.
     * @return the expression.
     */
    static AttrExpression parse(String value) {
        var values = value.split("\\.");

        // Parse a single-level expression ("foo" or "bar[0]").
        if (values.length == 1) {
            return new ToString(value, parsePart(value));
        }

        // Parse the multi-level expression ("foo.bar.baz[9]").
        var result = new AndThen(parsePart(values[0]), parsePart(values[1]));
        for (var i = 2; i < values.length; i++) {
            result = new AndThen(result, parsePart(values[i]));
        }

        // Set the toString value on the final result.
        return new ToString(value, result);
    }

    private static AttrExpression parsePart(String part) {
        int position = part.indexOf('[');
        if (position == -1) {
            return new GetKey(part);
        } else {
            String numberString = part.substring(position + 1, part.length() - 1);
            int index = Integer.parseInt(numberString);
            String key = part.substring(0, position);
            return new AndThen(new GetKey(key), new GetIndex(index));
        }
    }

    record ToString(String original, AttrExpression delegate) implements AttrExpression {
        @Override
        public Object apply(Object o) {
            return delegate.apply(o);
        }

        @Override
        public String toString() {
            return original;
        }
    }

    record AndThen(AttrExpression left, AttrExpression right) implements AttrExpression {
        @Override
        public Object apply(Object o) {
            var result = left.apply(o);
            if (result != null) {
                result = right.apply(result);
            }
            return result;
        }
    }

    record GetKey(String key) implements AttrExpression {
        @Override
        @SuppressWarnings("rawtypes")
        public Object apply(Object o) {
            if (o instanceof Map m) {
                return m.get(key);
            } else if (o instanceof URI u) {
                return EndpointUtils.getUriProperty(u, key);
            } else {
                return null;
            }
        }
    }

    record GetIndex(int index) implements AttrExpression {
        @Override
        @SuppressWarnings("rawtypes")
        public Object apply(Object o) {
            if (o instanceof List l && l.size() > index) {
                return l.get(index);
            }
            return null;
        }
    }
}
