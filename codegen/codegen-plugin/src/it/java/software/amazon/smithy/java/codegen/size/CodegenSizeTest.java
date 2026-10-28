/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.size;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

class CodegenSizeTest {

    private static final int EXIT_USAGE = 2;
    private static final int EXIT_REGRESSION = 3;

    @TempDir
    Path dir;

    @Test
    void unchangedSizesPassWithoutChange() throws IOException {
        ObjectNode base = report(ok("ec2", 5000), ok("s3", 1000));
        ObjectNode head = report(ok("ec2", 5000), ok("s3", 1000));

        Run run = compare(base, head);

        assertEquals(0, run.exit);
        assertEquals("unchanged", run.status);
        assertTrue(run.markdown.contains("**No change**"), run.markdown);
        assertTrue(run.markdown.contains("| Largest jar increase | | | none |"), run.markdown);
    }

    @Test
    void sizeChangesNameTheLargestMovers() throws IOException {
        ObjectNode base = report(ok("ec2", 5000), ok("iam", 2000), ok("s3", 1000));
        ObjectNode head = report(ok("ec2", 4000), ok("iam", 2000), ok("s3", 1500));

        Run run = compare(base, head);

        assertEquals(0, run.exit);
        assertEquals("changed", run.status);
        assertTrue(run.markdown.contains("| Largest jar increase: `s3` | 1000 B | 1.5 KB | +500 B (+50.00%) |"),
                run.markdown);
        assertTrue(run.markdown.contains("| Largest jar decrease: `ec2` | 4.9 KB | 3.9 KB | -1000 B (-20.00%) |"),
                run.markdown);
        assertTrue(run.markdown.contains("Totals cover the 3 services that compile on both base and PR."),
                run.markdown);
    }

    @Test
    void failureOnHeadThatCompiledOnBaseIsRegression() throws IOException {
        ObjectNode base = report(ok("ec2", 5000), ok("s3", 1000));
        ObjectNode head = report(ok("ec2", 5000), failed("s3"));

        Run run = compare(base, head);

        assertEquals(EXIT_REGRESSION, run.exit);
        assertEquals("regression", run.status);
        assertTrue(run.markdown.contains("**Failing on the PR but compiling on the base:** `s3`"), run.markdown);
        assertTrue(run.markdown.contains("**`s3`** failed during compile:"), run.markdown);
        assertTrue(run.markdown.contains("cannot find symbol"), run.markdown);
    }

    @Test
    void knownFailureFailingOnBothSidesIsExcluded() throws IOException {
        ObjectNode base = report(ok("ec2", 5000), failedListed("clouddirectory"));
        ObjectNode head = report(ok("ec2", 5000), failedListed("clouddirectory"));

        Run run = compare(base, head);

        assertEquals(0, run.exit);
        assertEquals("unchanged", run.status);
        assertTrue(run.markdown.contains("Known failures excluded: `clouddirectory`."), run.markdown);
        assertFalse(run.markdown.contains("Failing on both base and PR"), run.markdown);
        assertFalse(run.markdown.contains("Errors on the PR"), run.markdown);
    }

    @Test
    void knownFailureThatCompiledOnBaseIsRegression() throws IOException {
        ObjectNode base = report(ok("deadline", 3000), ok("ec2", 5000));
        ObjectNode head = report(failedListed("deadline"), ok("ec2", 5000));

        Run run = compare(base, head);

        assertEquals(EXIT_REGRESSION, run.exit);
        assertEquals("regression", run.status);
        assertTrue(run.markdown.contains("**Failing on the PR but compiling on the base:** `deadline`"),
                run.markdown);
        assertFalse(run.markdown.contains("Known failures excluded"), run.markdown);
    }

    @Test
    void knownFailureThatCompilesIsReportedAsFixedAndStale() throws IOException {
        ObjectNode base = report(failedListed("deadline"), ok("ec2", 5000));
        ObjectNode head = report(okListed("deadline", 3000), ok("ec2", 5000));

        Run run = compare(base, head);

        assertEquals(0, run.exit);
        assertEquals("changed", run.status);
        assertTrue(run.markdown.contains("Compiling on the PR but failing on the base: `deadline`."), run.markdown);
        assertTrue(run.markdown.contains(
                "Listed as known failures but compiling, so the entries can be removed: `deadline`."),
                run.markdown);
    }

    @Test
    void unlistedFailureOnBothSidesIsPreexisting() throws IOException {
        ObjectNode base = report(ok("ec2", 5000), failed("s3"));
        ObjectNode head = report(ok("ec2", 5000), failed("s3"));

        Run run = compare(base, head);

        assertEquals(0, run.exit);
        assertEquals("unchanged", run.status);
        assertTrue(run.markdown.contains("Failing on both base and PR: `s3`."), run.markdown);
        assertTrue(run.markdown.contains("**`s3`** failed during compile:"), run.markdown);
    }

    @Test
    void modelsNewOnHeadAreListedAndFailRegressions() throws IOException {
        ObjectNode base = report(ok("ec2", 5000), ok("old", 100));
        ObjectNode head = report(ok("ec2", 5000), ok("added", 200), failed("broken"));

        Run run = compare(base, head);

        assertEquals(EXIT_REGRESSION, run.exit);
        assertTrue(run.markdown.contains("**Failing on the PR but compiling on the base:** `broken`"),
                run.markdown);
        assertTrue(run.markdown.contains("Only in the PR model set: `added`, `broken`."), run.markdown);
        assertTrue(run.markdown.contains("Only in the base model set: `old`."), run.markdown);
    }

    @Test
    void headAloneTreatsUnlistedFailuresAsRegressions() throws IOException {
        ObjectNode head = report(ok("ec2", 5000), failedListed("clouddirectory"), failed("s3"));

        Run run = compare(null, head);

        assertEquals(EXIT_REGRESSION, run.exit);
        assertEquals("regression", run.status);
        assertTrue(run.markdown.contains("no base report was available"), run.markdown);
        assertTrue(run.markdown.contains("**Failing on the PR:** `s3`"), run.markdown);
        assertTrue(run.markdown.contains("Known failures excluded: `clouddirectory`."), run.markdown);
    }

    @Test
    void headAloneWithoutFailuresIsReportedAsChanged() throws IOException {
        ObjectNode head = report(ok("ec2", 5000), ok("s3", 1000));

        Run run = compare(null, head);

        assertEquals(0, run.exit);
        assertEquals("changed", run.status);
        assertTrue(run.markdown.contains("| Metric | PR |"), run.markdown);
        assertTrue(run.markdown.contains("| Total jar size | 5.9 KB |"), run.markdown);
        assertTrue(run.markdown.contains("2 services compile."), run.markdown);
    }

    @Test
    void baseWrittenByAnotherFormatIsReportedAsHeadAlone() throws IOException {
        ObjectNode base = report(CodegenSize.FORMAT - 1, ok("ec2", 5000), ok("s3", 1000));
        ObjectNode head = report(ok("ec2", 5000), ok("s3", 1000));

        Run run = compare(base, head);

        assertEquals(0, run.exit);
        assertEquals("changed", run.status);
        assertTrue(run.markdown.contains("the base report has format " + (CodegenSize.FORMAT - 1)
                + " but this tool reads format " + CodegenSize.FORMAT
                + ", so the PR is reported without a comparison."), run.markdown);
        assertTrue(run.markdown.contains("| Metric | PR |"), run.markdown);
    }

    @Test
    void headWrittenByAnotherFormatIsUsageError() throws IOException {
        ObjectNode base = report(ok("ec2", 5000));
        ObjectNode head = report(CodegenSize.FORMAT + 1, ok("ec2", 5000));

        Run run = compare(base, head);

        assertEquals(EXIT_USAGE, run.exit);
        assertNull(run.status);
    }

    @Test
    void differentInputsAreFlagged() throws IOException {
        ObjectNode base = report(ok("ec2", 5000), ok("s3", 1000)).toBuilder()
                .withMember("models",
                        Node.objectNodeBuilder()
                                .withMember("source", "models")
                                .withMember("count", 2)
                                .withMember("bom", "1.0.1")
                                .build())
                .build();
        ObjectNode head = report(ok("ec2", 5000), ok("s3", 1000));

        Run run = compare(base, head);

        assertEquals(0, run.exit);
        assertTrue(run.markdown.contains(
                "> **Warning:** the base report was produced with different inputs: aws-api-models 1.0.1."),
                run.markdown);
    }

    private record Run(int exit, String status, String markdown) {}

    private Run compare(ObjectNode base, ObjectNode head) throws IOException {
        Path markdown = dir.resolve("report.md");
        Path status = dir.resolve("status");
        List<String> args = new ArrayList<>();
        args.add("compare");
        if (base != null) {
            args.add(write("base.json", base).toString());
        }
        args.add(write("head.json", head).toString());
        args.add("--markdown");
        args.add(markdown.toString());
        args.add("--status");
        args.add(status.toString());
        int exit = CodegenSize.run(args.toArray(String[]::new));
        return new Run(exit,
                Files.exists(status) ? Files.readString(status).strip() : null,
                Files.exists(markdown) ? Files.readString(markdown) : "");
    }

    private Path write(String name, ObjectNode report) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, Node.prettyPrintJson(report));
        return file;
    }

    @SafeVarargs
    private static ObjectNode report(Map.Entry<String, ObjectNode>... services) {
        return report(CodegenSize.FORMAT, services);
    }

    @SafeVarargs
    private static ObjectNode report(int format, Map.Entry<String, ObjectNode>... services) {
        ObjectNode.Builder servicesNode = Node.objectNodeBuilder();
        for (Map.Entry<String, ObjectNode> service : services) {
            servicesNode.withMember(service.getKey(), service.getValue());
        }
        return Node.objectNodeBuilder()
                .withMember("tool", "codegen-size")
                .withMember("format", format)
                .withMember("modes", ArrayNode.fromStrings("client", "server"))
                .withMember("sizeModes", ArrayNode.fromStrings("client"))
                .withMember("javac",
                        Node.objectNodeBuilder()
                                .withMember("version", "25.0.1+8")
                                .withMember("flags", ArrayNode.fromStrings("--release", "21"))
                                .build())
                .withMember("models",
                        Node.objectNodeBuilder()
                                .withMember("source", "models")
                                .withMember("count", services.length)
                                .withMember("bom", "1.0.0")
                                .build())
                .withMember("services", servicesNode.build())
                .build();
    }

    private static Map.Entry<String, ObjectNode> ok(String name, long jarBytes) {
        return Map.entry(name, measured(name, jarBytes, false));
    }

    private static Map.Entry<String, ObjectNode> okListed(String name, long jarBytes) {
        return Map.entry(name, measured(name, jarBytes, true));
    }

    private static Map.Entry<String, ObjectNode> failed(String name) {
        return Map.entry(name, failure(name, false));
    }

    private static Map.Entry<String, ObjectNode> failedListed(String name) {
        return Map.entry(name, failure(name, true));
    }

    private static ObjectNode measured(String name, long jarBytes, boolean listed) {
        ObjectNode.Builder node = Node.objectNodeBuilder()
                .withMember("model", model(name))
                .withMember("totals",
                        Node.objectNodeBuilder()
                                .withMember("sourceFiles", 10)
                                .withMember("sourceBytes", jarBytes * 4)
                                .withMember("resourceFiles", 1)
                                .withMember("classes", 12)
                                .withMember("classBytes", jarBytes * 2)
                                .withMember("jarBytes", jarBytes)
                                .build());
        if (listed) {
            node.withMember("knownFailure", true);
        }
        return node.build();
    }

    private static ObjectNode failure(String name, boolean listed) {
        ObjectNode.Builder node = Node.objectNodeBuilder()
                .withMember("model", model(name))
                .withMember("failure",
                        Node.objectNodeBuilder()
                                .withMember("stage", "compile")
                                .withMember("message",
                                        "string:///gen/" + name + "/Foo.java:1: error: cannot find symbol")
                                .build());
        if (listed) {
            node.withMember("knownFailure", true);
        }
        return node.build();
    }

    private static ObjectNode model(String name) {
        return Node.objectNodeBuilder()
                .withMember("file", name + "-1.0.0.jar")
                .withMember("artifact", "software.amazon.api.models:" + name + ":1.0.0")
                .withMember("service", "com.amazonaws." + name + "#" + name)
                .withMember("namespace", "gen." + name)
                .build();
    }
}
