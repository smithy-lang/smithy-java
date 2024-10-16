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
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.IoUtils;
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Settings for {@code JavaCodegenPlugin}.
 */
@SmithyUnstableApi
public final class JavaCodegenSettings {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaCodegenSettings.class);

    private static final String SERVICE = "service";
    private static final String NAME = "name";
    private static final String NAMESPACE = "namespace";
    private static final String HEADER_FILE = "headerFile";
    private static final String NON_NULL_ANNOTATION = "nonNullAnnotation";
    private static final String DEFAULT_PROTOCOL = "protocol";
    private static final String TRANSPORT = "transport";
    private static final String DEFAULT_PLUGINS = "defaultPlugins";
    private static final String DEFAULT_SETTINGS = "defaultSettings";
    private static final String RELATIVE_DATE = "relativeDate";
    private static final String RELATIVE_VERSION = "relativeVersion";

    private static final List<String> PROPERTIES = List.of(
        SERVICE,
        NAME,
        NAMESPACE,
        HEADER_FILE,
        NON_NULL_ANNOTATION,
        DEFAULT_PROTOCOL,
        TRANSPORT,
        DEFAULT_PLUGINS,
        DEFAULT_SETTINGS,
        RELATIVE_DATE,
        RELATIVE_VERSION
    );

    private final ShapeId service;
    private final String name;
    private final String packageNamespace;
    private final String header;
    private final Symbol nonNullAnnotationSymbol;
    private final ShapeId defaultProtocol;
    private final String transportName;
    private final ObjectNode transportSettings;
    private final List<String> defaultPlugins;
    private final List<String> defaultSettings;
    private final String relativeDate;
    private final String relativeVersion;

    private JavaCodegenSettings(Builder builder) {
        this.service = Objects.requireNonNull(builder.service);
        this.name = StringUtils.capitalize(Objects.requireNonNullElse(builder.name, service.getName()));
        this.packageNamespace = Objects.requireNonNull(builder.packageNamespace);
        this.header = getHeader(builder.headerFilePath, builder.sourceLocation);
        this.nonNullAnnotationSymbol = buildSymbolFromFullyQualifiedName(builder.nonNullAnnotationFullyQualifiedName);
        this.defaultProtocol = builder.defaultProtocol;
        this.transportName = builder.transportName;
        this.transportSettings = builder.transportSettings;
        this.defaultPlugins = Collections.unmodifiableList(builder.defaultPlugins);
        this.defaultSettings = Collections.unmodifiableList(builder.defaultSettings);
        this.relativeDate = builder.relativeDate;
        this.relativeVersion = builder.relativeVersion;
    }

    /**
     * Creates a settings object from a plugin settings node
     *
     * @param settingsNode Settings node to load
     * @return Parsed settings
     */
    public static JavaCodegenSettings fromNode(ObjectNode settingsNode) {
        var builder = new Builder();
        settingsNode.warnIfAdditionalProperties(PROPERTIES)
            .expectStringMember(SERVICE, builder::service)
            .getStringMember(NAME, builder::name)
            .expectStringMember(NAMESPACE, builder::packageNamespace)
            .getStringMember(HEADER_FILE, builder::headerFilePath)
            .getStringMember(NON_NULL_ANNOTATION, builder::nonNullAnnotation)
            .getStringMember(DEFAULT_PROTOCOL, builder::defaultProtocol)
            .getObjectMember(TRANSPORT, builder::transportNode)
            .getArrayMember(DEFAULT_PLUGINS, n -> n.expectStringNode().getValue(), builder::defaultPlugins)
            .getArrayMember(DEFAULT_SETTINGS, n -> n.expectStringNode().getValue(), builder::defaultSettings)
            .getStringMember(RELATIVE_DATE, builder::relativeDate)
            .getStringMember(RELATIVE_VERSION, builder::relativeVersion);

        builder.sourceLocation(settingsNode.getSourceLocation().getFilename());

        return builder.build();
    }

    public ShapeId service() {
        return service;
    }

    public String name() {
        return name;
    }

    public String packageNamespace() {
        return packageNamespace;
    }

    public String header() {
        return header;
    }

    public Symbol nonNullAnnotationSymbol() {
        return nonNullAnnotationSymbol;
    }

    public ShapeId defaultProtocol() {
        return defaultProtocol;
    }

    public String transport() {
        return transportName;
    }

    public ObjectNode transportSettings() {
        return transportSettings;
    }

    public List<String> defaultPlugins() {
        return defaultPlugins;
    }

    public List<String> defaultSettings() {
        return defaultSettings;
    }

    public String relativeDate() {
        return relativeDate;
    }

    public String relativeVersion() {
        return relativeVersion;
    }

    private static Symbol buildSymbolFromFullyQualifiedName(String fullyQualifiedName) {
        if (fullyQualifiedName == null || StringUtils.isEmpty(fullyQualifiedName)) {
            return null;
        }
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
        LOGGER.trace("Reading header file: {}" + file.getAbsolutePath());
        return IoUtils.readUtf8File(file.getAbsolutePath());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ShapeId service;
        private String name;
        private String packageNamespace;
        private String headerFilePath;
        private String sourceLocation;
        private String nonNullAnnotationFullyQualifiedName;
        private ShapeId defaultProtocol;
        private String transportName;
        private ObjectNode transportSettings;
        private List<String> defaultPlugins = new ArrayList<>();
        private List<String> defaultSettings = new ArrayList<>();
        private String relativeDate;
        private String relativeVersion;

        public Builder service(String string) {
            this.service = ShapeId.from(string);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder packageNamespace(String packageNamespace) {
            this.packageNamespace = packageNamespace;
            return this;
        }

        public Builder defaultProtocol(String protocol) {
            this.defaultProtocol = ShapeId.from(protocol);
            return this;
        }

        public Builder transportNode(ObjectNode transportNode) {
            if (transportNode.getMembers().size() > 1) {
                throw new CodegenException(
                    "Only a single transport can be configured at a time. Found "
                        + transportNode.getMembers().keySet()
                );
            }
            transportName = transportNode.getMembers()
                .keySet()
                .stream()
                .findFirst()
                .map(StringNode::getValue)
                .orElse(null);
            transportSettings = transportNode.expectObjectMember(transportName);
            return this;
        }

        public Builder headerFilePath(String headerFilePath) {
            this.headerFilePath = headerFilePath;
            return this;
        }

        public Builder sourceLocation(String sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder nonNullAnnotation(String fullyQualifiedName) {
            this.nonNullAnnotationFullyQualifiedName = fullyQualifiedName;
            return this;
        }

        public Builder defaultPlugins(List<String> strings) {
            this.defaultPlugins.addAll(strings);
            return this;
        }

        public Builder defaultSettings(List<String> nodes) {
            this.defaultSettings.addAll(nodes);
            return this;
        }

        public Builder relativeDate(String relativeDate) {
            if (!CodegenUtils.isISO8601Date(relativeDate)) {
                throw new IllegalArgumentException(
                    "Provided relativeDate: `"
                        + relativeDate
                        + "` does not match semver format."
                );
            }
            this.relativeDate = relativeDate;
            return this;
        }

        public Builder relativeVersion(String relativeVersion) {
            if (!CodegenUtils.isSemVer(relativeVersion)) {
                throw new IllegalArgumentException(
                    "Provided relativeVersion: `"
                        + relativeVersion
                        + "` does not match semver format."
                );
            }
            this.relativeVersion = relativeVersion;
            return this;
        }

        public JavaCodegenSettings build() {
            return new JavaCodegenSettings(this);
        }
    }
}
