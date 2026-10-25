package software.amazon.smithy.java.example.standalone.model;

import software.amazon.smithy.java.core.schema.IntEnumValue;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * Requires a default branch in switches over enum variant types.
 * This keeps switches valid when the model adds values.
 */
final class $HiddenIntEnum_a50bb5c2 extends IntEnumValue implements IntEnumType {
    private $HiddenIntEnum_a50bb5c2() {
        super(0);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        throw new UnsupportedOperationException();
    }
}
