/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.rulesengine.Bytecode;
import software.amazon.smithy.java.rulesengine.JavaEndpointResolverGenerator;

final class GeneratedResolverFactory {
    private static final String PACKAGE = "software.amazon.smithy.java.aws.client.rulesengine.generated";

    private GeneratedResolverFactory() {}

    static EndpointResolver create(Bytecode bytecode, String className) {
        try {
            Path output = Files.createTempDirectory("smithy-generated-endpoint-");
            Path sourceDir = output.resolve(PACKAGE.replace('.', '/'));
            Files.createDirectories(sourceDir);
            Path sourceFile = sourceDir.resolve(className + ".java");
            Files.write(sourceDir.resolve(className + ".bdd"), bytecode.getBytecode());
            Files.writeString(
                    sourceFile,
                    new JavaEndpointResolverGenerator(bytecode).generate(PACKAGE, className));

            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("A JDK is required to compile generated endpoint resolvers");
            }
            var diagnostics = new StringBuilder();
            int result = compiler.run(
                    null,
                    null,
                    new java.io.OutputStream() {
                        @Override
                        public void write(int value) {
                            diagnostics.append((char) value);
                        }
                    },
                    "-proc:none",
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-d",
                    output.toString(),
                    sourceFile.toString());
            if (result != 0) {
                throw new IllegalStateException("Generated resolver compilation failed:\n" + diagnostics);
            }

            var loader = new URLClassLoader(
                    new java.net.URL[] {output.toUri().toURL()},
                    GeneratedResolverFactory.class.getClassLoader());
            Class<?> resolverClass = Class.forName(PACKAGE + "." + className, true, loader);
            return (EndpointResolver) resolverClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException | IOException e) {
            throw new IllegalStateException("Unable to create generated endpoint resolver", e);
        }
    }
}
