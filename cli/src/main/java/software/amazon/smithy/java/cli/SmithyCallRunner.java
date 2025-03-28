/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import picocli.CommandLine;

public class SmithyCallRunner {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new SmithyCall()).execute(args);
        System.exit(exitCode);
    }
}
