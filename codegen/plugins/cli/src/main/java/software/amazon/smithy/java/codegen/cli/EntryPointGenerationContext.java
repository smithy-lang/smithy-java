/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.cli;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * TODO: DOCS
 */
@SmithyInternalApi
public final class EntryPointGenerationContext {
    private final CliCodegenSettings settings;
    private final WriterDelegator<JavaWriter> writerDelegator;
    private final List<Symbol> serviceSymbols = new ArrayList<>();

    EntryPointGenerationContext(
            Model model,
            CliCodegenSettings settings,
            FileManifest fileManifest,
            List<Symbol> serviceSymbols
    ) {
        this.settings = settings;
        this.serviceSymbols.addAll(serviceSymbols);

        // It doesn't really matter which settings we use here so just use the first.
        var subSetting = settings.settings().get(0);

        // Create temp symbol provider
        var serviceShape = model.expectShape(subSetting.service()).asServiceShape().orElseThrow();
        var symbolProvider = new CliJavaSymbolProvider(model, serviceShape, settings.namespace(), "root");

        this.writerDelegator = new WriterDelegator<>(
                fileManifest,
                symbolProvider,
                (filename, namespace) -> new JavaWriter(subSetting, namespace, filename));
    }

    public CliCodegenSettings settings() {
        return settings;
    }

    public WriterDelegator<JavaWriter> writerDelegator() {
        return writerDelegator;
    }

    public List<Symbol> serviceSymbols() {
        return serviceSymbols;
    }
}
