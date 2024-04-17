/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;


public class JsonSerdeTest {

//    @ParameterizedTest(name = "{0}")
//    @MethodSource("source")
//    public void testRunner(String filename, Callable<?> callable) throws Exception {
//        callable.call();
//    }
//
//    public static Stream<?> source() {
//        return Stream.of();
//    }
//
//    public static Stream<Object[]> source(Class<?> contextClass) {
//        ClassLoader classLoader = contextClass.getClassLoader();
//        var url = Objects.requireNonNull(contextClass.getResource("json"));
//        if (!url.getProtocol().equals("file")) {
//            throw new IllegalArgumentException("Only file URLs are supported by the testrunner: " + url);
//        }
//
//        try {
//            return addTestCasesFromDirectory(Paths.get(url.toURI()));
//        } catch (URISyntaxException e) {
//            throw new RuntimeException(e);
//        }
//        return SmithyTestSuite.runner()
//                .addTestCasesFromUrl()
//                .parameterizedTestSource();
//    }
//
//    public  addTestCasesFromDirectory(Path modelDirectory) {
//        try (Stream<Path> files = Files.walk(modelDirectory)) {
//            files
//                    .filter(Files::isRegularFile)
//                    .filter(file -> {
//                        String filename = file.toString();
//                        return filename.endsWith(".json") || filename.endsWith(".smithy");
//                    })
//                    .map(file -> SmithyTestCase.fromModelFile(file.toString()))
//                    .forEach(this::addTestCase);
//            return this;
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public class SerdeJavaTestCase {
//    }
}
