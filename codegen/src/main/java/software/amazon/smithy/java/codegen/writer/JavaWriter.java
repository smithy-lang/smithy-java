/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public class JavaWriter extends SymbolWriter<JavaWriter, JavaImportContainer> {

    private final String packageNamespace;

    public JavaWriter(JavaCodegenSettings settings, String packageNamespace) {
        super(new JavaImportContainer(settings, packageNamespace));

        this.packageNamespace = packageNamespace;
        trimBlankLines();
        trimTrailingSpaces();

    }

    /**
     * A factory class to create {@link JavaWriter}s.
     */
    public static final class Factory implements SymbolWriter.Factory<JavaWriter> {

        private final JavaCodegenSettings settings;

        /**
         * @param settings The python plugin settings.
         */
        public Factory(JavaCodegenSettings settings) {
            this.settings = settings;
        }

        @Override
        public JavaWriter apply(String filename, String namespace) {
            return new JavaWriter(settings, namespace);
        }
    }
}
