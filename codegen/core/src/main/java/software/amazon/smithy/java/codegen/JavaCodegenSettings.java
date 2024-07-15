/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.io.File;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.IoUtils;
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Settings for {@code JavaCodegenPlugin}.
 */
@SmithyUnstableApi
public final class JavaCodegenSettings {
    private static final System.Logger LOGGER = System.getLogger(JavaCodegenSettings.class.getName());

    private static final String SERVICE = "service";
    private static final String NAMESPACE = "namespace";
    private static final String HEADER_FILE = "headerFile";
    private static final String NON_NULL_ANNOTATION = "nonNullAnnotation";

    private final ShapeId service;
    private final String packageNamespace;
    private final String header;

    private final Symbol nonNullAnnotationSymbol;

    JavaCodegenSettings(
        ShapeId service,
        String packageNamespace,
        String headerFile,
        String sourceLocation,
        String nonNullAnnotationFullyQualifiedName
    ) {
        this.service = Objects.requireNonNull(service);
        this.packageNamespace = Objects.requireNonNull(packageNamespace);
        this.header = getHeader(headerFile, Objects.requireNonNull(sourceLocation));

        if (!StringUtils.isEmpty(nonNullAnnotationFullyQualifiedName)) {
            nonNullAnnotationSymbol = buildSymbolFromFullyQualifiedName(nonNullAnnotationFullyQualifiedName);
        } else {
            nonNullAnnotationSymbol = null;
        }
    }

    /**
     * Creates a settings object from a plugin settings node
     *
     * @param settingsNode Settings node to load
     * @return Parsed settings
     */
    public static JavaCodegenSettings fromNode(ObjectNode settingsNode) {
        settingsNode.warnIfAdditionalProperties(List.of(SERVICE, NAMESPACE, HEADER_FILE, NON_NULL_ANNOTATION));
        return new JavaCodegenSettings(
            settingsNode.expectStringMember(SERVICE).expectShapeId(),
            settingsNode.expectStringMember(NAMESPACE).getValue(),
            settingsNode.getStringMemberOrDefault(HEADER_FILE, null),
            settingsNode.getSourceLocation().getFilename(),
            settingsNode.getStringMemberOrDefault(NON_NULL_ANNOTATION, "")
        );
    }

    public ShapeId service() {
        return service;
    }

    public String packageNamespace() {
        return packageNamespace;
    }

    public String header() {
        return header;
    }

    public Symbol getNonNullAnnotationSymbol() {
        return nonNullAnnotationSymbol;
    }

    private static Symbol buildSymbolFromFullyQualifiedName(String fullyQualifiedName) {
        String[] parts = fullyQualifiedName.split("\\.");
        String name = parts[parts.length - 1];
        String namespace = fullyQualifiedName.substring(0, fullyQualifiedName.length() - name.length() - 1);
        return Symbol.builder()
            .name(name)
            .namespace(namespace, ".")
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .build();
    }

    private static String getHeader(String headerFile, String sourceLocation) {
        if (headerFile == null) {
            return null;
        }
        var file = new File(new File(sourceLocation).getParent(), headerFile);
        if (!file.exists()) {
            throw new CodegenException("Header file " + file.getAbsolutePath() + " does not exist.");
        }
        LOGGER.log(System.Logger.Level.TRACE, () -> "Reading header file: " + file.getAbsolutePath());
        return IoUtils.readUtf8File(file.getAbsolutePath());
    }

}
