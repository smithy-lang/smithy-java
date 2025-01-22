/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathExpression;
import software.amazon.smithy.model.shapes.ShapeType;

// TODO: Add compliance test test runner and reach full compliance with JMESPath spec.
public class TestJMESPathDocumentVisitor {
    @Test
    void testSubExpressionExpr() {
        var doc = Document.of(
                Map.of("status",
                        Document.of("FAILED"),
                        "nested",
                        Document.of(Map.of("id", Document.of("idValue")))));
        var exp = JmespathExpression.parse("nested.id");
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals("idValue", value.asString());
    }

    static List<Arguments> indexSource() {
        return List.of(
                Arguments.of("[0]", "A"),
                Arguments.of("[1]", "B"),
                Arguments.of("[2]", "C"),
                Arguments.of("[-1]", "C"),
                Arguments.of("[-2]", "B"),
                Arguments.of("[-3]", "A"));
    }

    @ParameterizedTest
    @MethodSource("indexSource")
    void testIndexExpression(String str, String expected) {
        var doc = Document.of(List.of(Document.of("A"), Document.of("B"), Document.of("C")));
        var exp = JmespathExpression.parse(str);
        var actual = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, actual.asString());
    }

    static List<Arguments> sliceSource() {
        return List.of(
                Arguments.of("[0:4:1]", List.of(Document.of(0), Document.of(1), Document.of(2), Document.of(3))),
                Arguments.of("[0:4]", List.of(Document.of(0), Document.of(1), Document.of(2), Document.of(3))),
                Arguments.of("[0:3]", List.of(Document.of(0), Document.of(1), Document.of(2))),
                Arguments.of("[:2]", List.of(Document.of(0), Document.of(1))),
                Arguments.of("[::2]", List.of(Document.of(0), Document.of(2))),
                Arguments.of("[::-1]", List.of(Document.of(3), Document.of(2), Document.of(1), Document.of(0))),
                Arguments.of("[-2:]", List.of(Document.of(2), Document.of(3))));
    }

    @ParameterizedTest
    @MethodSource("sliceSource")
    void testSliceExpression(String str, List<Document> expected) {
        var doc = Document.of(List.of(Document.of(0), Document.of(1), Document.of(2), Document.of(3)));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, value.asList());
    }

    static List<Arguments> multiselectListSource() {
        return List.of(
                Arguments.of("[foo, bar]", List.of(Document.of("a"), Document.of("b"))),
                Arguments.of("[foo, baz[0]]", List.of(Document.of("a"), Document.of("c"))),
                Arguments.of("[foo, qux.quux]", List.of(Document.of("a"), Document.of("d"))));
    }

    @ParameterizedTest
    @MethodSource("multiselectListSource")
    void testMultiselectList(String str, List<Document> expected) {
        // {"foo": "a", "bar": "b", "baz": ["c"], "qux": {"quux": "d"}}"
        var doc = Document.of(Map.of(
                "foo",
                Document.of("a"),
                "bar",
                Document.of("b"),
                "baz",
                Document.of(List.of(Document.of("c"))),
                "qux",
                Document.of(Map.of("quux", Document.of("d")))));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, value.asList());
    }

    static List<Arguments> multiselectMapSource() {
        return List.of(
                Arguments.of("{foo: foo, bar: bar}", Map.of("foo", Document.of("a"), "bar", Document.of("b"))),
                Arguments.of("{foo: foo, firstBaz: baz[0]}",
                        Map.of("foo", Document.of("a"), "firstBaz", Document.of("c"))),
                Arguments.of("{foo: foo, \"qux.quux\": qux.quux}",
                        Map.of("foo", Document.of("a"), "qux.quux", Document.of("d"))));
    }

    @ParameterizedTest
    @MethodSource("multiselectMapSource")
    void testMultiselectMap(String str, Map<String, Document> expected) {
        // {"foo": "a", "bar": "b", "baz": ["c"], "qux": {"quux": "d"}}"
        var doc = Document.of(Map.of(
                "foo",
                Document.of("a"),
                "bar",
                Document.of("b"),
                "baz",
                Document.of(List.of(Document.of("c"))),
                "qux",
                Document.of(Map.of("quux", Document.of("d")))));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, value.asStringMap());
    }

    static List<Arguments> orSource() {
        return List.of(
                Arguments.of("foo || qux", "foo-value"),
                Arguments.of("qux || bar", "bar-value"),
                Arguments.of("foo || bar", "foo-value"),
                Arguments.of("qux || quux", null),
                Arguments.of("qux || quux || bar", "bar-value"),
                Arguments.of("qux || myList[-1]", "two"),
                Arguments.of("bar || myList[-1]", "bar-value"));
    }

    @ParameterizedTest
    @MethodSource("orSource")
    void testOrExpression(String str, String expected) {
        var doc = Document.of(Map.of(
                "foo",
                Document.of("foo-value"),
                "bar",
                Document.of("bar-value"),
                "myList",
                Document.of(List.of(Document.of("one"), Document.of("two")))));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));

        if (expected == null) {
            assertNull(value);
        } else {
            assertEquals(expected, value.asString());
        }
    }

    /// search(, {"True": true, "False": false}) -> false
    //search(Number && EmptyList, {"Number": 5, EmptyList: []}) -> []
    static List<Arguments> andSource() {
        return List.of(
                Arguments.of("True && False", false));
    }

    @ParameterizedTest
    @MethodSource("andSource")
    void testAndExpression(String str, boolean expected) {
        var doc = Document.of(Map.of(
                "True",
                Document.of(true),
                "False",
                Document.of(false)));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, value.asBoolean());
    }

    static List<Arguments> comparisonSource() {
        return List.of(
                Arguments.of("foo!=bar", true),
                Arguments.of("foo==bar", false),
                Arguments.of("bar=='b'", true),
                Arguments.of("baz<`2`", true),
                Arguments.of("qux==`2`", false),
                Arguments.of("qux<=`2`", true),
                Arguments.of("baz==`1.0`", true));
    }

    @ParameterizedTest
    @MethodSource("comparisonSource")
    void testComparisonExpression(String str, boolean expected) {
        var doc = Document.of(Map.of(
                "foo",
                Document.of("a"),
                "bar",
                Document.of("b"),
                "baz",
                Document.of(1.0),
                "qux",
                Document.of(1)));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, value.asBoolean());
    }

    static List<Arguments> notSource() {
        return List.of(
                Arguments.of("!True", false),
                Arguments.of("!False", true),
                Arguments.of("!Number", false),
                Arguments.of("!EmptyList", true));
    }

    @ParameterizedTest
    @MethodSource("notSource")
    void testNotExpression(String str, boolean expected) {
        var doc = Document.of(Map.of(
                "True",
                Document.of(true),
                "False",
                Document.of(false),
                "Number",
                Document.of(5),
                "EmptyList",
                Document.of(List.of())));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, value.asBoolean());
    }

    static List<Arguments> filterProjectionSource() {
        return List.of(
                Arguments.of("foo[?a==b]",
                        List.of(Document.of(
                                Map.of(
                                        "a",
                                        Document.of("char"),
                                        "b",
                                        Document.of("char"))))));
    }

    @ParameterizedTest
    @MethodSource("filterProjectionSource")
    void testFilterProjectionExpression(String str, List<Document> expected) {
        var doc = Document.of(Map.of(
                "foo",
                Document.of(List.of(
                        Document.of(Map.of(
                                "a",
                                Document.of("char"),
                                "b",
                                Document.of("char"))),
                        Document.of(Map.of(
                                "a",
                                Document.of(2),
                                "b",
                                Document.of(1))),
                        Document.of(Map.of(
                                "a",
                                Document.of(1),
                                "b",
                                Document.of(2)))))));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(doc));
        assertEquals(expected, value.asList());
    }

    static List<Arguments> flattenSource() {
        return List.of(
                Arguments.of("foos[]", List.of(Document.of(1), Document.of(2))),
                Arguments.of("bars[].baz", List.of(Document.of(1), Document.of(2))));
    }

    @ParameterizedTest
    @MethodSource("flattenSource")
    void testFlattenExpression(String str, List<Document> expected) {
        var testDocument = Document.of(Map.of(
                "foos",
                Document.of(List.of(
                        Document.of(1),
                        Document.of(2))),
                "bars",
                Document.of(List.of(
                        Document.of(Map.of("baz", Document.of(1))),
                        Document.of(Map.of("baz", Document.of(2)))))));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(testDocument));
        assertEquals(expected, value.asList());
    }

    static List<Arguments> functionSource() {
        return List.of(
                Arguments.of("abs(neg)", Document.of(1)),
                Arguments.of("contains(foo, 'b')", Document.of(true)),
                Arguments.of("contains(foo, 'n')", Document.of(false)),
                Arguments.of("contains(str, 'my')", Document.of(true)),
                Arguments.of("ceil(`1.001`)", Document.of(2.0)),
                Arguments.of("ceil(`1.9`)", Document.of(2.0)),
                Arguments.of("ceil(`1`)", Document.of(1.0)),
                Arguments.of("ceil(`\"abc\"`)", null),
                Arguments.of("ends_with(str, 'Str')", Document.of(true)),
                Arguments.of("ends_with(str, 'bar')", Document.of(false)),
                Arguments.of("starts_with(str, 'my')", Document.of(true)),
                Arguments.of("starts_with(str, 'bar')", Document.of(false)),
                Arguments.of("floor(`1.001`)", Document.of(1.0)),
                Arguments.of("floor(`1.9`)", Document.of(1.0)),
                Arguments.of("floor(`1`)", Document.of(1.0)),
                Arguments.of("keys(@)",
                        Document.of(List
                                .of(Document.of("neg"), Document.of("str"), Document.of("foo"), Document.of("nums")))),
                Arguments.of("length(nums)", Document.of(3)),
                Arguments.of("max(nums)", Document.of(3)),
                Arguments.of("min(nums)", Document.of(1)),
                Arguments.of("not_null(no, not, this, str)", Document.of("myStr")),
                Arguments.of("reverse(nums)", Document.of(List.of(Document.of(3), Document.of(1), Document.of(2)))),
                Arguments.of("sort(nums)", Document.of(List.of(Document.of(1), Document.of(2), Document.of(3)))),
                Arguments.of("type(neg)", Document.of("number")),
                Arguments.of("type(foo)", Document.of("array")),
                Arguments.of("type(zip)", Document.of("null")),
                Arguments.of("type(str)", Document.of("string")));
    }

    @ParameterizedTest
    @MethodSource("functionSource")
    void testFunctionExpression(String str, Document expected) {
        var testDocument = Document.of(Map.of(
                "neg",
                Document.of(-1),
                "foo",
                Document.of(List.of(Document.of("a"), Document.of("b"))),
                "str",
                Document.of("myStr"),
                "nums",
                Document.of(List.of(Document.of(2), Document.of(1), Document.of(3)))));
        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new JMESPathDocumentVisitor(testDocument));
        if (expected == null) {
            assertNull(value);
        } else if (expected.type().equals(ShapeType.LIST)) {
            assertThat(expected.asList(), containsInAnyOrder(value.asList().toArray()));
        } else {
            assertEquals(expected, value);
        }
    }
}
