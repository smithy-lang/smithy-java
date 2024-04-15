/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;


/**
 * Defines shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {

    static final SdkSchema BIRTHDAY = SdkSchema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.example#Birthday")
        .traits(

        )
        .build();

    static final SdkSchema LIST_OF_STRING = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id("smithy.example#ListOfString")

            .members(SdkSchema.memberBuilder(0, "member", PreludeSchemas.STRING))
            .build();

    static final SdkSchema MAP_LIST_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapListString")

        .members(
                SdkSchema.memberBuilder(0, "key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder(1, "value", SharedSchemas.LIST_OF_STRING)
        )
        .build();

    static final SdkSchema SET_OF_STRING = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id("smithy.example#SetOfString")
            .traits(

            )
            .members(SdkSchema.memberBuilder(0, "member", PreludeSchemas.STRING))
            .build();

    static final SdkSchema STREAM = SdkSchema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.example#Stream")
        .traits(

        )
        .build();

    private SharedSchemas() {}
}

