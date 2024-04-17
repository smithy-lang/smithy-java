/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;


/**
 * Defines shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {

    static final SdkSchema BIRTHDAY = SdkSchema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.example#Birthday")
        .traits(
            new TimestampFormatTrait("date-time"),
            new SensitiveTrait()
        )
        .build();

    static final SdkSchema LIST_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#ListOfString")
        .members(SdkSchema.memberBuilder(0, "member", PreludeSchemas.STRING)
        .traits(
            new LengthTrait.Provider().createTrait(
                ShapeId.from("smithy.api#length"),
                Node.objectNodeBuilder()
                    .withMember("min", 1)
                    .build()
            )
        ))
        .build();

    static final SdkSchema MAP_LIST_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapListString")
        .members(
                SdkSchema.memberBuilder(0, "key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder(0, "value", SharedSchemas.LIST_OF_STRING)
        )
        .build();

    static final SdkSchema SET_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#SetOfString")
        .traits(
            new UniqueItemsTrait()
        )
        .members(SdkSchema.memberBuilder(0, "member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema STREAM = SdkSchema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.example#Stream")
        .build();

    private SharedSchemas() {}
}

