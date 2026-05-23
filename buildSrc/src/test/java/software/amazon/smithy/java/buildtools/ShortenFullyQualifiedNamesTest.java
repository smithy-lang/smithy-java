/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShortenFullyQualifiedNamesTest {

    @Test
    void basicShortening() {
        var input = """
                package com.example;

                class Foo {
                    java.util.List<String> x;
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;

                class Foo {
                    List<String> x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void multipleTypes() {
        var input = """
                package com.example;

                class Foo {
                    java.util.List<String> x;
                    java.util.Map<String, String> y;
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;
                import java.util.Map;

                class Foo {
                    List<String> x;
                    Map<String, String> y;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void javaLangTypeNoImportNeeded() {
        var input = """
                package com.example;

                class Foo {
                    java.lang.String x;
                }
                """;
        var expected = """
                package com.example;

                class Foo {
                    String x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void javaLangSubpackageNeedsImport() {
        var input = """
                package com.example;

                class Foo {
                    java.lang.reflect.Method m;
                }
                """;
        var expected = """
                package com.example;

                import java.lang.reflect.Method;

                class Foo {
                    Method m;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void samePackageNoImportNeeded() {
        var input = """
                package com.example;

                class Foo {
                    com.example.Bar x;
                }
                """;
        var expected = """
                package com.example;

                class Foo {
                    Bar x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void existingImportNoDuplicate() {
        var input = """
                package com.example;

                import java.util.List;

                class Foo {
                    java.util.List<String> x;
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;

                class Foo {
                    List<String> x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void existingImportConflict() {
        var input = """
                package com.example;

                import java.util.List;

                class Foo {
                    List<String> a;
                    java.awt.List b;
                }
                """;
        assertEquals(input, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void locallyDefinedTypeConflict() {
        var input = """
                package com.example;

                class List {
                    java.util.List<String> x;
                }
                """;
        assertEquals(input, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void nameCollisionPicksMostUsed() {
        var input = """
                package com.example;

                class Foo {
                    java.util.List<String> a;
                    java.util.List<Integer> b;
                    java.awt.List c;
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;

                class Foo {
                    List<String> a;
                    List<Integer> b;
                    java.awt.List c;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void wildcardImportCoverage() {
        var input = """
                package com.example;

                import java.util.*;

                class Foo {
                    java.util.List<String> x;
                }
                """;
        var expected = """
                package com.example;

                import java.util.*;

                class Foo {
                    List<String> x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void annotationShortening() {
        var input = """
                package com.example;

                class Foo {
                    @java.lang.SuppressWarnings("unchecked")
                    void bar() {}
                }
                """;
        var expected = """
                package com.example;

                class Foo {
                    @SuppressWarnings("unchecked")
                    void bar() {}
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void genericTypeArguments() {
        var input = """
                package com.example;

                import java.util.Map;

                class Foo {
                    Map<java.util.List<String>, java.util.Set<Integer>> x;
                }
                """;
        var expected = """
                package com.example;

                import java.util.Map;
                import java.util.List;
                import java.util.Set;

                class Foo {
                    Map<List<String>, Set<Integer>> x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void newExpression() {
        var input = """
                package com.example;

                class Foo {
                    Object x = new java.util.ArrayList<>();
                }
                """;
        var expected = """
                package com.example;

                import java.util.ArrayList;

                class Foo {
                    Object x = new ArrayList<>();
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void returnTypeAndParameters() {
        var input = """
                package com.example;

                class Foo {
                    java.util.List<String> bar(java.util.Map<String, String> input) {
                        return null;
                    }
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;
                import java.util.Map;

                class Foo {
                    List<String> bar(Map<String, String> input) {
                        return null;
                    }
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void extendsClause() {
        var input = """
                package com.example;

                class Foo extends java.util.AbstractList<String> {
                }
                """;
        var expected = """
                package com.example;

                import java.util.AbstractList;

                class Foo extends AbstractList<String> {
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void implementsClause() {
        var input = """
                package com.example;

                class Foo implements java.io.Serializable {
                }
                """;
        var expected = """
                package com.example;

                import java.io.Serializable;

                class Foo implements Serializable {
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void throwsClause() {
        var input = """
                package com.example;

                class Foo {
                    void bar() throws java.io.IOException {
                    }
                }
                """;
        var expected = """
                package com.example;

                import java.io.IOException;

                class Foo {
                    void bar() throws IOException {
                    }
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void instanceOfExpression() {
        var input = """
                package com.example;

                class Foo {
                    boolean bar(Object o) {
                        return o instanceof java.util.List;
                    }
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;

                class Foo {
                    boolean bar(Object o) {
                        return o instanceof List;
                    }
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void noOpWhenNoQualifiedNames() {
        var input = """
                package com.example;

                import java.util.List;

                class Foo {
                    List<String> x;
                }
                """;
        assertEquals(input, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void innerClassHandling() {
        var input = """
                package com.example;

                class Foo {
                    java.util.Map.Entry<String, String> x;
                }
                """;
        var expected = """
                package com.example;

                import java.util.Map;

                class Foo {
                    Map.Entry<String, String> x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void typeCast() {
        var input = """
                package com.example;

                class Foo {
                    Object bar(Object o) {
                        return (java.util.List<String>) o;
                    }
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;

                class Foo {
                    Object bar(Object o) {
                        return (List<String>) o;
                    }
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void staticMethodCallOnQualifiedClass() {
        var input = """
                package com.example;

                class Foo {
                    Object bar() {
                        return java.util.Collections.emptyList();
                    }
                }
                """;
        var expected = """
                package com.example;

                import java.util.Collections;

                class Foo {
                    Object bar() {
                        return Collections.emptyList();
                    }
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void mixedShortenableAndNot() {
        var input = """
                package com.example;

                import java.util.List;

                class Foo {
                    List<String> a;
                    java.util.List<Integer> b;
                    java.awt.List c;
                    java.util.Map<String, String> d;
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;
                import java.util.Map;

                class Foo {
                    List<String> a;
                    List<Integer> b;
                    java.awt.List c;
                    Map<String, String> d;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void noPackageDeclaration() {
        var input = """
                class Foo {
                    java.util.List<String> x;
                }
                """;
        var expected = """
                import java.util.List;

                class Foo {
                    List<String> x;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void multipleOccurrencesOfSameType() {
        var input = """
                package com.example;

                class Foo {
                    java.util.List<String> a;
                    java.util.List<Integer> b;
                    java.util.List<Object> c;
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;

                class Foo {
                    List<String> a;
                    List<Integer> b;
                    List<Object> c;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void doesNotShadowImplicitJavaLangType() {
        var input = """
                package com.example;

                class Foo {
                    String x;
                    com.example.String y;
                }
                """;
        assertEquals(input, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void doesNotShadowImplicitSamePackageType() {
        var input = """
                package com.example;

                class Foo {
                    Bar x;
                    com.other.Bar y;
                }
                """;
        assertEquals(input, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void staticFieldAccess() {
        var input = """
                package com.example;

                class Foo {
                    int x = java.lang.Integer.MAX_VALUE;
                }
                """;
        var expected = """
                package com.example;

                class Foo {
                    int x = Integer.MAX_VALUE;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void staticFieldAccessNeedsImport() {
        var input = """
                package com.example;

                class Foo {
                    java.io.PrintStream out = java.lang.System.out;
                }
                """;
        var expected = """
                package com.example;

                import java.io.PrintStream;

                class Foo {
                    PrintStream out = System.out;
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void methodReference() {
        var input = """
                package com.example;

                import java.util.List;

                class Foo {
                    void bar(List<String> list) {
                        list.stream().filter(java.util.Objects::nonNull);
                    }
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;
                import java.util.Objects;

                class Foo {
                    void bar(List<String> list) {
                        list.stream().filter(Objects::nonNull);
                    }
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void permitsClause() {
        var input = """
                package com.example;

                sealed class Foo permits com.example.bar.Baz {
                }
                """;
        var expected = """
                package com.example;

                import com.example.bar.Baz;

                sealed class Foo permits Baz {
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void typeAnnotationOnQualifiedNameIsSkipped() {
        // java.util.@NonNull List has the annotation interleaved in the source text,
        // so we skip shortening to avoid dropping the annotation
        var input = """
                package com.example;

                import org.jspecify.annotations.NonNull;

                class Foo {
                    java.util.@NonNull List<String> x;
                }
                """;
        assertEquals(input, ShortenFullyQualifiedNames.shortenNames(input));
    }

    @Test
    void enumConstantDoesNotBlockShortening() {
        var input = """
                package com.example;

                class Foo {
                    void bar() {
                        System.out.println(ACTIVE);
                        java.util.List<String> x = null;
                    }
                }
                """;
        var expected = """
                package com.example;

                import java.util.List;

                class Foo {
                    void bar() {
                        System.out.println(ACTIVE);
                        List<String> x = null;
                    }
                }
                """;
        assertEquals(expected, ShortenFullyQualifiedNames.shortenNames(input));
    }
}
