/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.smithy.modelbundle.api.model.SmithyBundle;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyInternalApi
@SmithyUnstableApi
public abstract class ModelBundler {

    public abstract SmithyBundle bundle();

    protected static String loadModel(String path) {
        try (var reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(ModelBundler.class.getResourceAsStream(path)),
                StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
