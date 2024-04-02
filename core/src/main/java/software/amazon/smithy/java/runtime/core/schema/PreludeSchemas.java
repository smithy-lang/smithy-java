package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.model.shapes.ShapeType;

/**
 * {@link SdkSchema} definitions for the Smithy prelude
 */
public final class PreludeSchemas {

    public static final SdkSchema STRING = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
    public static final SdkSchema BLOB = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
    public static final SdkSchema BIG_INTEGER = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
    public static final SdkSchema BIG_DECIMAL = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
    public static final SdkSchema TIMESTAMP = SdkSchema.builder().type(ShapeType.TIMESTAMP).id("smithy.api#String").build();


    public static final SdkSchema INTEGER = SdkSchema.builder().type(ShapeType.INTEGER).id("smithy.api#Integer").build();


    private PreludeSchemas() {
        // Class should not be instantiated.
    }
}
