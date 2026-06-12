/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;

/**
 * A Gradle ValueSource that reads codegen modes from smithy-build.json files.
 *
 * <p>Using a ValueSource ensures Gradle's configuration cache properly tracks
 * the smithy-build.json file as a configuration input and invalidates the cache
 * when the file's modes change.
 */
public abstract class SmithyBuildModesValueSource implements ValueSource<Set<String>, SmithyBuildModesValueSource.Params> {

    private static final String JAVA_CODEGEN_PLUGIN_NAME = "java-codegen";

    public interface Params extends ValueSourceParameters {
        SetProperty<File> getSmithyBuildConfigs();
    }

    @Override
    public Set<String> obtain() {
        Set<String> allModes = new HashSet<>();
        for (File config : getParameters().getSmithyBuildConfigs().get()) {
            if (!config.exists()) {
                continue;
            }
            try {
                String content = Files.readString(config.toPath());
                ObjectNode root = Node.parseJsonWithComments(content).expectObjectNode();

                root.getObjectMember("plugins")
                        .flatMap(plugins -> plugins.getObjectMember(JAVA_CODEGEN_PLUGIN_NAME))
                        .flatMap(codegen -> codegen.getArrayMember("modes"))
                        .map(SmithyBuildModesValueSource::extractModes)
                        .ifPresent(allModes::addAll);
                root.getObjectMember("projections").ifPresent(projections -> {
                    for (Node value : projections.getMembers().values()) {
                        value.expectObjectNode()
                                .getObjectMember("plugins")
                                .flatMap(plugins -> plugins.getObjectMember(JAVA_CODEGEN_PLUGIN_NAME))
                                .flatMap(codegen -> codegen.getArrayMember("modes"))
                                .map(SmithyBuildModesValueSource::extractModes)
                                .ifPresent(allModes::addAll);
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return allModes.isEmpty() ? Set.of("types") : allModes;
    }

    private static Set<String> extractModes(ArrayNode modesArray) {
        Set<String> modes = new HashSet<>();
        for (Node element : modesArray.getElements()) {
            element.asStringNode()
                    .map(s -> s.getValue().toLowerCase(Locale.ENGLISH))
                    .ifPresent(modes::add);
        }
        return modes;
    }
}
