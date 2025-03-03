/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.cli;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.model.node.ObjectNode;

// TODO: Default transport?
public class CliCodegenSettings {
    // Top level properties for generator
    private static final String NAME = "name";
    private static final String NAMESPACE = "namespace";
    private static final String HEADER_FILE = "headerFile";
    private static final String PLUGINS = "plugins";
    private static final String RELATIVE_DATE = "relativeDate";
    private static final String RELATIVE_VERSION = "relativeVersion";
    private static final String EDITION = "edition";
    private static final String SERVICES = "services";
    private static final String TRANSPORT = "transport";
    private static final String STANDALONE = "standalone";
    private static final String ENDPOINT = "endpoint";
    private static final String DESCRIPTION = "description";
    private static final List<String> PROPERTIES = List.of(
            NAME,
            NAMESPACE,
            HEADER_FILE,
            RELATIVE_DATE,
            RELATIVE_VERSION,
            EDITION,
            SERVICES,
            TRANSPORT,
            PLUGINS,
            STANDALONE,
            ENDPOINT,
            DESCRIPTION);

    // Service settings specific properties
    private static final String SERVICE = "service";
    private static final String PROTOCOL = "protocol";
    private static final String DEFAULT_PLUGINS = "defaultPlugins";
    private static final List<String> SERVICE_PROPERTIES = List.of(
            SERVICE,
            PROTOCOL,
            TRANSPORT,
            DEFAULT_PLUGINS);

    private final String name;
    private final String namespace;
    private final String headerFilePath;
    private final String sourceLocation;
    private final List<String> plugins = new ArrayList<>();
    private final String relativeDate;
    private final String relativeVersion;
    private final String edition;
    private final ObjectNode transport;
    private final boolean standalone;
    private final List<String> argumentReceivers = new ArrayList<>();
    private final List<JavaCodegenSettings> settings;
    private final String endpoint;
    private final String description;

    private CliCodegenSettings(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "`name` cannot be null");
        this.namespace = Objects.requireNonNull(builder.namespace, "`namespace` cannot be null");
        this.headerFilePath = builder.headerFilePath;
        this.sourceLocation = builder.sourceLocation;
        this.relativeDate = builder.relativeDate;
        this.relativeVersion = builder.relativeVersion;
        this.edition = Objects.requireNonNullElse(builder.edition, "LATEST");
        this.plugins.addAll(builder.plugins);
        this.transport = builder.transport;
        this.standalone = builder.standalone;
        this.endpoint = builder.endpoint;
        this.description = builder.description;
        this.settings = parseServiceSettings(builder.services);
    }

    private List<JavaCodegenSettings> parseServiceSettings(ObjectNode node) {
        List<JavaCodegenSettings> settings = new ArrayList<>();
        if (node.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one service definition.");
        }

        for (var serviceEntry : node.getStringMap().entrySet()) {
            var builder = JavaCodegenSettings.builder();
            // Set defaults from base cli settings
            builder.name(serviceEntry.getKey())
                    .packageNamespace(namespace + "." + serviceEntry.getKey().toLowerCase())
                    .edition(edition);
            if (headerFilePath != null) {
                builder.headerFilePath(headerFilePath);
            }
            if (sourceLocation != null) {
                builder.sourceLocation(sourceLocation);
            }
            if (relativeDate != null) {
                builder.relativeDate(relativeDate);
            }
            if (relativeVersion != null) {
                builder.relativeVersion(relativeVersion);
            }

            // Add service-specific settings.
            var objectNode = serviceEntry.getValue().expectObjectNode();
            objectNode.warnIfAdditionalProperties(SERVICE_PROPERTIES)
                    .expectStringMember(SERVICE, builder::service)
                    // TODO: Ideally this wouldnt be required
                    .expectStringMember(PROTOCOL, builder::defaultProtocol)
                    .getArrayMember(DEFAULT_PLUGINS, n -> n.expectStringNode().getValue(), builder::defaultPlugins);

            // Can set per-service transport or just use shared transport setting
            if (transport != null) {
                builder.transportNode(transport);
            } else {
                objectNode.expectObjectMember(TRANSPORT, builder::transportNode);
            }

            settings.add(builder.build());
        }
        return settings;
    }

    public List<JavaCodegenSettings> settings() {
        return settings;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    public String headerFilePath() {
        return headerFilePath;
    }

    public String sourceLocation() {
        return sourceLocation;
    }

    public List<String> plugins() {
        return plugins;
    }

    public boolean multiServiceCli() {
        return settings.size() > 1 || !standalone;
    }

    public String endpoint() {
        return endpoint;
    }

    public String description() {
        return description;
    }

    public static CliCodegenSettings fromObjectNode(ObjectNode node) {
        var builder = builder();
        node.warnIfAdditionalProperties(PROPERTIES)
                .expectStringMember(NAME, builder::name)
                .expectStringMember(NAMESPACE, builder::namespace)
                .getStringMember(HEADER_FILE, builder::headerFilePath)
                .getArrayMember(PLUGINS, n -> n.expectStringNode().getValue(), builder::plugins)
                .getStringMember(RELATIVE_DATE, builder::relativeDate)
                .getStringMember(RELATIVE_VERSION, builder::relativeVersion)
                .getStringMember(EDITION, builder::edition)
                .expectObjectMember(SERVICES, builder::services)
                .getObjectMember(TRANSPORT, builder::transportNode)
                .getBooleanMember(STANDALONE, builder::standalone)
                .getStringMember(ENDPOINT, builder::endpoint)
                .getStringMember(DESCRIPTION, builder::description);
        builder.sourceLocation(node.getSourceLocation().getFilename());

        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String namespace;
        private String headerFilePath;
        private String sourceLocation;
        private final List<String> plugins = new ArrayList<>();
        private String relativeDate;
        private String relativeVersion;
        private String edition;
        private ObjectNode services;
        private ObjectNode transport;
        private boolean standalone;
        private String endpoint;
        private String description;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
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

        public Builder plugins(List<String> strings) {
            this.plugins.addAll(strings);
            return this;
        }

        public Builder relativeDate(String relativeDate) {
            if (!CodegenUtils.isISO8601Date(relativeDate)) {
                throw new IllegalArgumentException(
                        "Provided relativeDate: `"
                                + relativeDate
                                + "` does not match semver format.");
            }
            this.relativeDate = relativeDate;
            return this;
        }

        public Builder relativeVersion(String relativeVersion) {
            if (!CodegenUtils.isSemVer(relativeVersion)) {
                throw new IllegalArgumentException(
                        "Provided relativeVersion: `"
                                + relativeVersion
                                + "` does not match semver format.");
            }
            this.relativeVersion = relativeVersion;
            return this;
        }

        public Builder edition(String string) {
            this.edition = string;
            return this;
        }

        public Builder services(ObjectNode services) {
            this.services = services;
            return this;
        }

        public Builder transportNode(ObjectNode transportNode) {
            if (transportNode.getMembers().size() > 1) {
                throw new CodegenException(
                        "Only a single transport can be configured at a time. Found "
                                + transportNode.getMembers().keySet());
            }
            this.transport = transportNode;
            return this;
        }

        public Builder standalone(boolean standalone) {
            this.standalone = standalone;
            return this;
        }

        public Builder endpoint(String endpoint) {
            try {
                new URI(endpoint);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Endpoint: " + endpoint + "must be a valid URI");
            }
            this.endpoint = endpoint;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public CliCodegenSettings build() {
            return new CliCodegenSettings(this);
        }
    }
}
