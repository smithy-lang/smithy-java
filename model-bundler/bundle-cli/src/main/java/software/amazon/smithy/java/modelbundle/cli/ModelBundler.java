/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.modelbundle.cli;

import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.modelbundle.api.Bundlers;

public final class ModelBundler {
    public static void main(String[] args) {
        // TODO: arg parsing
        // remove the output argument from the provided arguments
        if (args.length < 2) {
            throw new RuntimeException("Expected at least two arguments: a bundler name and an output location");
        }

        var bundlerName = args[0];
        var output = args[1];

        String[] argsWithoutOutput = new String[args.length - 2];
        if (args.length > 2) {
            System.arraycopy(args, 2, argsWithoutOutput, 0, argsWithoutOutput.length);
        }

        var bundle = Bundlers.builder().build().getProvider(bundlerName, argsWithoutOutput).bundle();
        var codec = JsonCodec.builder().build();
        try (var os = new BufferedOutputStream(Files.newOutputStream(Path.of(output)));
                var serializer = codec.createSerializer(os)) {
            bundle.serialize(serializer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
