/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Schema representing a resource shape in a Smithy model.
 *
 * <p>Note: Resource Schemas are treated as
 */
final class ResourceSchema extends Schema {
    private final Map<String, Schema> identifiers;
    private final List<Schema> identifierList;
    private final Map<String, Schema> properties;
    private final List<Schema> propertyList;
    private final Schema resource;

    ResourceSchema(
        ShapeId id,
        TraitMap traits,
        List<MemberSchemaBuilder> identifierBuilders,
        List<MemberSchemaBuilder> propertyBuilders,
        Schema resource
    ) {
        super(ShapeType.RESOURCE, id, traits, Collections.emptyList(), Collections.emptySet());
        if (identifierBuilders.isEmpty()) {
            identifierList = Collections.emptyList();
            identifiers = Collections.emptyMap();
        } else {
            identifierList = new ArrayList<>(identifierBuilders.size());
            for (var builder : identifierBuilders) {
                identifierList.add(builder.build());
            }
            identifiers = SchemaBuilder.createMembers(identifierList);
        }
        if (propertyBuilders.isEmpty()) {
            propertyList = Collections.emptyList();
            properties = Collections.emptyMap();
        } else {
            propertyList = new ArrayList<>(propertyBuilders.size());
            for (var builder : identifierBuilders) {
                propertyList.add(builder.build());
            }
            properties = SchemaBuilder.createMembers(propertyList);
        }
        this.resource = resource;
    }

    @Override
    public Map<String, Schema> identifiers() {
        return identifiers;
    }

    @Override
    public Map<String, Schema> properties() {
        return properties;
    }

    // TODO: Flatten identifiers in resource schemas from parent resources
    @Override
    public Schema resource() {
        return resource;
    }
}
