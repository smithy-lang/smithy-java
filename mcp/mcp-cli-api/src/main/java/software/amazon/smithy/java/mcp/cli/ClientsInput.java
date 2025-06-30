/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.Set;
import picocli.CommandLine.Option;

public final class ClientsInput {
    @Option(names = {"--clients"},
            description = "Names of client configs to update. If not specified all client configs registered would be updated")
    public Set<String> clients = Set.of();

    @Option(names = "--print-client-config",
            description = "If specified will not edit the client configs and only print to console.")
    public boolean print = false;
}
