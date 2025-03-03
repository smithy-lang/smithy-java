/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.java.cli.arguments.ArgumentReceiver;
import software.amazon.smithy.java.cli.commands.Command;
import software.amazon.smithy.java.cli.formatting.ColorTheme;
import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * An immutable representation of configurations of a {@link CLI}.
 *
 * <p>It has well-defined configuration elements that every {@link CLI} needs.
 */
public class CliConfig {
    private final ColorTheme theme;
    private final URI endpoint;
    private final List<ClientPlugin> clientPlugins;
    private final List<ArgumentReceiver> argumentReceivers;
    private final List<Command> commands;

    private CliConfig(Builder builder) {
        this.theme = builder.theme;
        this.endpoint = builder.endpoint;
        this.clientPlugins = Collections.unmodifiableList(builder.clientPlugins);
        this.argumentReceivers = Collections.unmodifiableList(builder.argumentReceivers);
        this.commands = Collections.unmodifiableList(builder.commands);
    }

    public ColorTheme theme() {
        return theme;
    }

    public URI endpoint() {
        return endpoint;
    }

    public List<ClientPlugin> clientPlugins() {
        return clientPlugins;
    }

    public List<Command> commands() {
        return commands;
    }

    public List<ArgumentReceiver> argumentReceivers() {
        return argumentReceivers;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ColorTheme theme;
        private URI endpoint;
        private final List<ClientPlugin> clientPlugins = new ArrayList<>();
        private final List<ArgumentReceiver> argumentReceivers = new ArrayList<>();
        private final List<Command> commands = new ArrayList<>();

        private Builder() {}

        public Builder addClientPlugin(ClientPlugin clientPlugin) {
            clientPlugins.add(clientPlugin);
            return this;
        }

        public Builder addArgumentReceiver(ArgumentReceiver receiver) {
            this.argumentReceivers.add(receiver);
            return this;
        }

        public Builder addCommand(Command command) {
            this.commands.add(command);
            return this;
        }

        public Builder theme(ColorTheme theme) {
            this.theme = theme;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = URI.create(endpoint);
            return this;
        }

        public CliConfig build() {
            return new CliConfig(this);
        }
    }
}
