/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.framework.knowledge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.framework.traits.AddsImplicitErrorsTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.KnowledgeIndex;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ToShapeId;

/**
 * Provides an index of the implicit errors of a service.
 *
 * @see <a>TODO</a>
 */
public final class ImplicitErrorIndex implements KnowledgeIndex {

    private final Map<ShapeId, Set<ShapeId>> serviceImplicitErrorMap = new HashMap<>();

    private ImplicitErrorIndex(Model model) {
        for (var service : model.getServiceShapes()) {
            // Search one layer for `@adds
            for (var traitEntry : service.getAllTraits().entrySet()) {
                var traitShapeOptional = model.getShape(traitEntry.getKey());
                if (traitShapeOptional.isEmpty()) {
                    // Ignore traits not found in model. This can happen if customers
                    // has --allow-unknown-traits set to true.
                    continue;
                }
                var traitShape = traitShapeOptional.get();
                if (traitShape.hasTrait(AddsImplicitErrorsTrait.class)) {
                    var implicitErrorsTrait = traitShape.expectTrait(AddsImplicitErrorsTrait.class);
                    var implicitErrorList = serviceImplicitErrorMap.computeIfAbsent(
                        service.toShapeId(),
                        k -> new HashSet<>()
                    );
                    implicitErrorList.addAll(implicitErrorsTrait.getValues());
                }
            }
        }
    }

    public static ImplicitErrorIndex of(Model model) {
        return model.getKnowledge(ImplicitErrorIndex.class, ImplicitErrorIndex::new);
    }

    public Set<ShapeId> getImplicitErrorsForService(ToShapeId toShapeId) {
        return serviceImplicitErrorMap.computeIfAbsent(toShapeId.toShapeId(), k -> new HashSet<>());
    }
}
