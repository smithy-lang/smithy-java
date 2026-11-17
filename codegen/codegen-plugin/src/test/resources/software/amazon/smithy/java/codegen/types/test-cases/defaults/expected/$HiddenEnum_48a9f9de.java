package software.amazon.smithy.java.example.defaults.model;

import software.amazon.smithy.java.core.schema.EnumValue;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * Requires a default branch in switches over enum variant types.
 * This keeps switches valid when the model adds values.
 */
final class $HiddenEnum_48a9f9de extends EnumValue implements NestedEnum {
    private $HiddenEnum_48a9f9de() {
        super(null);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        throw new UnsupportedOperationException();
    }
}
