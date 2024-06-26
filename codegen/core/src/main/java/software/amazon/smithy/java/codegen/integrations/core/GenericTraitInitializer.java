/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

final class GenericTraitInitializer implements TraitInitializer<Trait> {
    private static final Map<ShapeId, Class<? extends TraitService>> serviceMap = new HashMap<>();

    static {
        // Add all trait services to a map, so they can be queried for a provider class
        ServiceLoader.load(TraitService.class, GenericTraitInitializer.class.getClassLoader())
            .forEach((service) -> serviceMap.put(service.getShapeId(), service.getClass()));
    }

    @Override
    public Class<Trait> traitClass() {
        return Trait.class;
    }

    @Override
    public void accept(JavaWriter writer, Trait trait) {
        var traitProviderClass = serviceMap.get(trait.toShapeId());
        if (traitProviderClass == null) {
            throw new UnsupportedOperationException("Could not find trait provider for " + trait);
        }
        writer.pushState();
        var template = """
            new ${?enclosing}${enclosing:T}.${name:L}${/enclosing}${^enclosing}${type:T}${/enclosing}().createTrait(
                ${shapeId:T}.from(${id:S}),
                ${nodeInitializer:C|}
            )""";
        writer.putContext("shapeId", ShapeId.class);
        writer.putContext("name", traitProviderClass.getSimpleName());
        writer.putContext("type", traitProviderClass);
        writer.putContext("id", trait.toShapeId());
        if (traitProviderClass.isMemberClass()) {
            writer.putContext("enclosing", traitProviderClass.getEnclosingClass());
        }
        writer.putContext("nodeInitializer", new NodeWriter(writer, trait.toNode()));
        writer.writeInline(template);
        writer.popState();
    }
}
