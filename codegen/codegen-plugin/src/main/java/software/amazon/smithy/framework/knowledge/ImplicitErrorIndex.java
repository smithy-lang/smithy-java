/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.framework.knowledge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.framework.traits.ImplicitErrorsTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.KnowledgeIndex;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ToShapeId;

/**
 * Provides an index of the implicit errors of a service.
 * TODO: Upstream to smithy-model package along with implicitError trait
 */
public final class ImplicitErrorIndex implements KnowledgeIndex {

    private final Map<ShapeId, Set<ShapeId>> serviceImplicitErrorMap;
    private final Set<ShapeId> implicitErrors;

    private ImplicitErrorIndex(Model model) {
        var serviceImplicitError = new HashMap<ShapeId, Set<ShapeId>>();
        var allImplicitErrors = new TreeSet<ShapeId>();

        for (var service : model.getServiceShapes()) {
            for (var traitEntry : service.getAllTraits().entrySet()) {
                var traitShapeOptional = model.getShape(traitEntry.getKey());
                if (traitShapeOptional.isEmpty()) {
                    // Ignore traits not found in model. This can happen if a user
                    // has --allow-unknown-traits set to true.
                    continue;
                }

                var traitShape = traitShapeOptional.get();
                if (traitShape.hasTrait(ImplicitErrorsTrait.class)) {
                    var implicitErrorsTrait = traitShape.expectTrait(ImplicitErrorsTrait.class);
                    var implicitErrorList = serviceImplicitError.computeIfAbsent(
                            service.toShapeId(),
                            k -> new TreeSet<>());
                    implicitErrorList.addAll(implicitErrorsTrait.getValues());
                    allImplicitErrors.addAll(implicitErrorsTrait.getValues());
                }
            }
        }

        var immutableServiceMap = new HashMap<ShapeId, Set<ShapeId>>();
        for (var entry : serviceImplicitError.entrySet()) {
            immutableServiceMap.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        this.serviceImplicitErrorMap = Collections.unmodifiableMap(immutableServiceMap);
        this.implicitErrors = Collections.unmodifiableSet(allImplicitErrors);
    }

    public static ImplicitErrorIndex of(Model model) {
        return model.getKnowledge(ImplicitErrorIndex.class, ImplicitErrorIndex::new);
    }

    public Set<ShapeId> getImplicitErrorsForService(ToShapeId toShapeId) {
        return serviceImplicitErrorMap.getOrDefault(toShapeId.toShapeId(), Collections.emptySet());
    }

    public boolean isImplicitError(ShapeId shapeId) {
        return implicitErrors.contains(shapeId);
    }
}
