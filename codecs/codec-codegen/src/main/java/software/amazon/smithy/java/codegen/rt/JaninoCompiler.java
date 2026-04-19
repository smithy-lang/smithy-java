/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.MapResourceFinder;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.Compiler;

/**
 * Compiles Java source code strings into Class objects at runtime using Janino.
 * Generated classes are loaded as hidden classes for GC-friendly lifecycle.
 */
public final class JaninoCompiler {

    private static final Logger LOG = Logger.getLogger(JaninoCompiler.class.getName());
    private static final boolean DUMP_SOURCE =
            Boolean.getBoolean("smithy.codegen.dumpSource");

    private JaninoCompiler() {}

    public static Class<?> compile(
            String className,
            String sourceCode,
            ClassLoader parentClassLoader,
            Class<?> lookupClass
    ) {
        if (DUMP_SOURCE) {
            dumpSource(className, sourceCode);
        }

        try {
            String resourcePath = className.replace('.', '/') + ".java";

            MapResourceFinder sourceFinder = new MapResourceFinder();
            sourceFinder.addResource(resourcePath, sourceCode);

            Map<String, byte[]> classes = new HashMap<>();
            MapResourceCreator classCreator = new MapResourceCreator(classes);

            Compiler compiler = new Compiler(
                    sourceFinder,
                    new ClassLoaderIClassLoader(parentClassLoader));
            compiler.setClassFileCreator(classCreator);
            compiler.setTargetVersion(21);
            compiler.setDebugSource(true);
            compiler.setDebugLines(true);

            compiler.compile(sourceFinder.resources().toArray(new Resource[0]));

            String classFilePath = className.replace('.', '/') + ".class";
            byte[] bytecode = classes.get(classFilePath);
            if (bytecode == null) {
                throw new RuntimeException("Compilation produced no output for " + className
                        + ". Available classes: " + classes.keySet());
            }

            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    lookupClass,
                    MethodHandles.lookup());
            MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                    bytecode,
                    true);
            return hiddenLookup.lookupClass();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile " + className + ": " + e.getMessage()
                    + "\n--- Source ---\n" + sourceCode, e);
        }
    }

    private static void dumpSource(String className, String sourceCode) {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "smithy-codegen-dump");
            Files.createDirectories(dir);
            Path file = dir.resolve(className.replace('.', '_') + ".java");
            Files.writeString(file, sourceCode);
            LOG.info("Dumped generated source to " + file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to dump source for " + className, e);
        }
    }
}
