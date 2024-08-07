/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.selector.Selector;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.IoUtils;
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Common settings for {@code JavaCodegenPlugin}'s.
 */
@SmithyUnstableApi
public final class JavaCodegenSettings {
    private static final System.Logger LOGGER = System.getLogger(JavaCodegenSettings.class.getName());

    private static final String SERVICE = "service";
    private static final String NAMESPACE = "namespace";
    private static final String HEADER_FILE = "headerFile";
    private static final String NON_NULL_ANNOTATION = "nonNullAnnotation";
    private static final String SHAPES = "shapes";
    private static final String SELECTOR = "selector";
    private static final List<String> PROPERTIES = List.of(
        SERVICE,
        NAMESPACE,
        HEADER_FILE,
        NON_NULL_ANNOTATION,
        SHAPES,
        SELECTOR
    );

    private final ShapeId service;
    private final String packageNamespace;
    private final String header;
    private final Symbol nonNullAnnotationSymbol;
    private final List<ShapeId> shapes = new ArrayList<>();
    private final Selector selector;

    JavaCodegenSettings(
        ShapeId service,
        String packageNamespace,
        String headerFile,
        String sourceLocation,
        String nonNullAnnotationFullyQualifiedName,
        List<ShapeId> shapes,
        Selector selector
    ) {
        this.service = Objects.requireNonNull(service);
        this.packageNamespace = Objects.requireNonNull(packageNamespace);
        this.header = getHeader(headerFile, Objects.requireNonNull(sourceLocation));

        if (!StringUtils.isEmpty(nonNullAnnotationFullyQualifiedName)) {
            nonNullAnnotationSymbol = buildSymbolFromFullyQualifiedName(nonNullAnnotationFullyQualifiedName);
        } else {
            nonNullAnnotationSymbol = null;
        }
        this.shapes.addAll(shapes);
        this.selector = selector;
    }

    public static JavaCodegenSettings fromNode(ObjectNode settingsNode) {
        settingsNode.warnIfAdditionalProperties(PROPERTIES);
        return fromNode(settingsNode.expectStringMember(SERVICE).expectShapeId(), settingsNode);
    }

    /**
     * Creates a settings object from a plugin settings node
     *
     * @param service service shape to use
     * @param settingsNode Settings node to load
     * @return Parsed settings
     */
    public static JavaCodegenSettings fromNode(ShapeId service, ObjectNode settingsNode) {
        settingsNode.warnIfAdditionalProperties(PROPERTIES);
        return new JavaCodegenSettings(
            service,
            settingsNode.expectStringMember(NAMESPACE).getValue(),
            settingsNode.getStringMemberOrDefault(HEADER_FILE, null),
            settingsNode.getSourceLocation().getFilename(),
            settingsNode.getStringMemberOrDefault(NON_NULL_ANNOTATION, ""),
            settingsNode.getArrayMember(SHAPES)
                .map(n -> n.getElementsAs(el -> el.expectStringNode().expectShapeId()))
                .orElse(Collections.emptyList()),
            Selector.parse(settingsNode.getStringMemberOrDefault(SELECTOR, "*"))
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

    public Selector selector() {
        return selector;
    }

    public List<ShapeId> shapes() {
        return shapes;
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
