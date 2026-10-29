package software.amazon.smithy.java.example.standalone.model;

import software.amazon.smithy.java.core.schema.EnumValue;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * Requires a default branch in switches over enum variant types.
 * This keeps switches valid when the model adds values.
 */
final class $HiddenEnum_6c1c4910 extends EnumValue implements EnumType, TextType {
    private $HiddenEnum_6c1c4910() {
        super(null);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        throw new UnsupportedOperationException();
    }
}
