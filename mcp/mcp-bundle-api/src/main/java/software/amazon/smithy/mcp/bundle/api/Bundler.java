/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.mcp.bundle.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyInternalApi
@SmithyUnstableApi
public abstract class Bundler {

    public abstract Bundle bundle();

    protected String loadModel(String path) {
        try (var reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(Bundler.class.getResourceAsStream(path)),
                StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
