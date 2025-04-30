/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.GetAttr;

abstract sealed class AttrExpression {

    abstract Object apply(Object o);

    static AttrExpression from(GetAttr getAttr) {
        var path = getAttr.getPath();
        if (path.isEmpty()) {
            throw new UnsupportedOperationException("Invalid getAttr expression: requires at least one part");
        } else if (path.size() == 1) {
            return from(getAttr.getPath().get(0));
        }

        // Parse the multi-level expression ("foo.bar.baz[9]").
        var result = new AndThen(from(path.get(0)), from(path.get(1)));
        for (var i = 2; i < path.size(); i++) {
            result = new AndThen(result, from(path.get(i)));
        }

        // Set the toString value on the final result.
        String str = getAttr.toString(); // in the form of something#path
        int position = str.lastIndexOf('#');
        result.tostringValue = str.substring(position, str.length() - 1);
        return result;
    }

    static AttrExpression from(GetAttr.Part part) {
        if (part instanceof GetAttr.Part.Key k) {
            return new GetKey(k.key().toString());
        } else if (part instanceof GetAttr.Part.Index i) {
            return new GetIndex(i.index());
        } else {
            throw new UnsupportedOperationException("Unexpected GetAttr part: " + part);
        }
    }

    static AttrExpression parse(String value) {
        var values = value.split("\\.");

        // Parse a single-level expression ("foo" or "bar[0]").
        if (values.length == 1) {
            return parsePart(value);
        }

        // Parse the multi-level expression ("foo.bar.baz[9]").
        var result = new AndThen(parsePart(values[0]), parsePart(values[1]));
        for (var i = 2; i < values.length; i++) {
            result = new AndThen(result, parsePart(values[i]));
        }
        // Set the toString value on the final result.
        result.tostringValue = value;
        return result;
    }

    static AttrExpression parsePart(String part) {
        int position = part.indexOf('[');
        if (position == -1) {
            return new GetKey(part);
        } else {
            String numberString = part.substring(position, part.length() - 1);
            int index = Integer.parseInt(numberString);
            String key = part.substring(0, position);
            return new AndThen(new GetKey(key), new GetIndex(index));
        }
    }

    static final class AndThen extends AttrExpression {
        String tostringValue;
        AttrExpression left;
        AttrExpression right;

        AndThen(AttrExpression left, AttrExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        Object apply(Object o) {
            var result = left.apply(o);
            if (result != null) {
                result = right.apply(result);
            }
            return result;
        }

        @Override
        public String toString() {
            return tostringValue;
        }
    }

    static final class GetKey extends AttrExpression {
        private final String key;

        GetKey(String key) {
            this.key = key;
        }

        @Override
        @SuppressWarnings("rawtypes")
        Object apply(Object o) {
            return o instanceof Map m ? m.get(key) : null;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    static final class GetIndex extends AttrExpression {
        private final int index;

        GetIndex(int index) {
            this.index = index;
        }

        @Override
        @SuppressWarnings("rawtypes")
        Object apply(Object o) {
            if (o instanceof List l && l.size() >= index) {
                return l.get(index);
            }
            return null;
        }

        @Override
        public String toString() {
            return "[" + index + "]";
        }
    }
}
