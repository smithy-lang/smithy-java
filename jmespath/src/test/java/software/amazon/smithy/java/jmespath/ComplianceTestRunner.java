/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NodeVisitor;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ShapeType;

class ComplianceTestRunner {
    private static final String DEFAULT_TEST_CASE_LOCATION = "compliance";
    private static final String SUBJECT_MEMBER = "given";
    private static final String CASES_MEMBER = "cases";
    private static final String EXPRESSION_MEMBER = "expression";
    private static final String RESULT_MEMBER = "result";
    private static final NodeToDocumentConverter CONVERTER = new NodeToDocumentConverter();
    // TODO: Remove these suppressions as remaining functions are supported
    private static final List<String> UNSUPPORTED_FUNCTIONS = List.of(
            "to_string",
            "to_array",
            "merge",
            "map");
    private final List<TestCase> testCases = new ArrayList<>();

    private ComplianceTestRunner() {}

    static ComplianceTestRunner runner() {
        return new ComplianceTestRunner();
    }

    public static Stream<Object[]> defaultParameterizedTestSource(Class<?> contextClass) {
        return ComplianceTestRunner.runner()
                .addTestCasesFromUrl(Objects.requireNonNull(contextClass.getResource(DEFAULT_TEST_CASE_LOCATION)))
                .parameterizedTestSource();
    }

    public ComplianceTestRunner addTestCasesFromUrl(URL url) {
        if (!url.getProtocol().equals("file")) {
            throw new IllegalArgumentException("Only file URLs are supported by the test runner: " + url);
        }

        try {
            return addTestCasesFromDirectory(Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public Stream<Object[]> parameterizedTestSource() {
        return testCases.stream().map(testCase -> new Object[] {testCase.name(), testCase});
    }

    public ComplianceTestRunner addTestCasesFromDirectory(Path directory) {
        for (var file : Objects.requireNonNull(directory.toFile().listFiles())) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                testCases.addAll(TestCase.from(file));
            }
        }
        return this;
    }

    private record TestCase(String testSuite, Node givenNode, Document given, String expression, Document expected)
            implements Runnable {
        public static List<TestCase> from(File file) {
            var testSuiteName = file.getName().substring(0, file.getName().lastIndexOf('.'));
            var testCases = new ArrayList<TestCase>();
            try (var is = new FileInputStream(file)) {
                var tests = Node.parse(is).expectArrayNode().getElementsAs(ObjectNode.class);

                for (var test : tests) {
                    var givenNode = test.expectMember(SUBJECT_MEMBER);
                    var given = CONVERTER.convert(givenNode);
                    for (var testCase : test.expectArrayMember(CASES_MEMBER).getElementsAs(ObjectNode.class)) {
                        var expression = testCase.expectStringMember(EXPRESSION_MEMBER).getValue();
                        // Filters out unsupported functions
                        // TODO: Remove once all built-in functions are supported
                        if (testSuiteName.equals("functions")
                                && UNSUPPORTED_FUNCTIONS.stream().anyMatch(expression::contains)) {
                            continue;
                        }
                        // Skip error cases as those are handled by smithy jmespath library
                        var resultOptional = testCase.getMember(RESULT_MEMBER);
                        if (resultOptional.isEmpty()) {
                            continue;
                        }
                        var expected = CONVERTER.convert(resultOptional.get());
                        testCases.add(new TestCase(testSuiteName, givenNode, given, expression, expected));
                    }
                }
                return testCases;
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Could not find test file.", e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private String name() {
            return testSuite + " (" + Node.printJson(givenNode) + ")[" + expression + "]";
        }

        @Override
        public void run() {
            var result = JMESPathDocumentQuery.query(expression, given);
            if (expected == null && result == null) {
                return;
            }
            if (!isEqual(expected, result)) {
                throw new AssertionError("Expected does not match actual. \n"
                        + "Expected: " + expected + "\n"
                        + "Actual: " + result + "\n"
                        + "For query: " + expression + "\n");
            }
        }
    }

    /**
     * Compare documents element by element so we can assert null values are equal
     */
    private static boolean isEqual(Document expected, Document actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        } else if (expected.type().equals(ShapeType.MAP) && actual.type().equals(ShapeType.MAP)) {
            for (var member : expected.getMemberNames()) {
                var expectedMember = expected.getMember(member);
                var actualMember = actual.getMember(member);
                if (!isEqual(expectedMember, actualMember)) {
                    return false;
                }
            }
            return true;
        } else if (expected.type().equals(ShapeType.LIST) && actual.type().equals(ShapeType.LIST)) {
            if (expected.size() != actual.size()) {
                return false;
            } else {
                for (int i = 0; i < expected.size(); i++) {
                    if (!isEqual(expected.asList().get(i), actual.asList().get(i))) {
                        return false;
                    }
                }
                return true;
            }
        } else if (isNumeric(expected) && isNumeric(actual)) {
            // Normalize all numbers to BigDecimal to make comparisons work.
            return new BigDecimal(expected.asNumber().toString())
                           .compareTo(new BigDecimal(actual.asNumber().toString())) == 0;
        }
        return Objects.equals(expected, actual);
    }

    private static boolean isNumeric(Document doc) {
        var type = doc.type();
        return type == ShapeType.BYTE || type == ShapeType.SHORT || type == ShapeType.INTEGER
                || type == ShapeType.LONG || type == ShapeType.BIG_INTEGER || type == ShapeType.BIG_DECIMAL
                || type == ShapeType.FLOAT || type == ShapeType.DOUBLE || type == ShapeType.INT_ENUM;
    }

    private static final class NodeToDocumentConverter implements NodeVisitor<Document> {
        private Document convert(Node node) {
            return node.accept(this);
        }

        @Override
        public Document arrayNode(ArrayNode node) {
            List<Document> result = new ArrayList<>();
            for (var item : node.getElements()) {
                result.add(item.accept(this));
            }
            return Document.of(result);
        }

        @Override
        public Document booleanNode(BooleanNode node) {
            return Document.of(node.getValue());
        }

        @Override
        public Document nullNode(NullNode node) {
            return null;
        }

        @Override
        public Document numberNode(NumberNode node) {
            return Document.ofNumber(node.getValue());
        }

        @Override
        public Document objectNode(ObjectNode node) {
            Map<String, Document> result = new HashMap<>();
            for (var entry : node.getMembers().entrySet()) {
                result.put(entry.getKey().getValue(), entry.getValue().accept(this));
            }
            return Document.of(result);
        }

        @Override
        public Document stringNode(StringNode node) {
            return Document.of(node.getValue());
        }
    }
}
