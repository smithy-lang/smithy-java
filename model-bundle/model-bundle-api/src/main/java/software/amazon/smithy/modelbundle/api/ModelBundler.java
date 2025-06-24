/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.AbstractShapeBuilder;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.modelbundle.api.model.SmithyBundle;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.ToSmithyBuilder;

@SmithyInternalApi
@SmithyUnstableApi
public abstract class ModelBundler {

    private static final Pattern CLEAN_HTML_PATTERN = Pattern.compile("<[^<]+?>", Pattern.DOTALL);
    private static final InternalLogger LOG = InternalLogger.getLogger(ModelBundler.class);

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

    protected static Model cleanAndFilterModel(
            Model model,
            ShapeId allowedServiceId,
            Set<String> allowedOperations,
            Set<String> blockedOperations,
            Set<String> allowedWords,
            Set<String> blockedWords
    ) {
        var builder = model.toBuilder();
        var allowedService = model.expectShape(allowedServiceId, ServiceShape.class);
        var serviceBuilder = allowedService.toBuilder();
        var operations = allowedService.getOperations();
        for (var serviceShape : model.getServiceShapes()) {
            if (!serviceShape.toShapeId().equals(allowedService.toShapeId())) {
                builder.removeShape(serviceShape.toShapeId());
            }
        }
        for (var op : model.getOperationShapes()) {
            var name = op.getId().getName();
            var allowed = isAllowed(name, allowedOperations, blockedOperations, allowedWords, blockedWords);
            if (allowed) {
                cleanDocumentation(op, builder);
                LOG.debug("Allowed API {}", name);
            } else {
                builder.removeShape(op.getId());
                serviceBuilder.removeOperation(op.getId());
                LOG.debug("Blocked API {}", name);
            }
        }
        var service = serviceBuilder.build();
        builder.addShape(service);
        cleanDocumentation(service, builder);
        return ModelTransformer.create().removeUnreferencedShapes(builder.build());
    }

    static boolean isAllowed(
            String operationName,
            Set<String> allowedOperations,
            Set<String> blockedOperations,
            Set<String> allowedPrefixes,
            Set<String> blockedPrefixes
    ) {
        var explicitlyAllowed = allowedOperations.contains(operationName);
        var explicitlyBlocked = blockedOperations.contains(operationName);
        var startsWithAllowedPrefix = allowedPrefixes.stream()
                .anyMatch(operationName::startsWith);
        var startsWithBlockedPrefix = blockedPrefixes.stream()
                .anyMatch(operationName::startsWith);

        if (explicitlyBlocked) {
            return false;
        } else if (explicitlyAllowed) {
            return true;
        } else if (startsWithBlockedPrefix) {
            return false;
        } else if (startsWithAllowedPrefix) {
            return true;
        }
        return allowedOperations.isEmpty() && allowedPrefixes.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void cleanDocumentation(Shape shape, Model.Builder builder) {
        if (shape instanceof ServiceShape || shape instanceof OperationShape || shape instanceof StructureShape) {
            shape.getTrait(DocumentationTrait.class).ifPresent(trait -> {
                var documentation = trait.getValue();
                var cleanedDocumentation = CLEAN_HTML_PATTERN.matcher(documentation).replaceAll("");
                var shapeBuilder = (AbstractShapeBuilder<?, Shape>) ((ToSmithyBuilder<?>) shape).toBuilder();
                builder.removeShape(shape.toShapeId());
                builder.addShape(shapeBuilder.addTrait(new DocumentationTrait(cleanedDocumentation)).build());
            });
        }
    }
}
