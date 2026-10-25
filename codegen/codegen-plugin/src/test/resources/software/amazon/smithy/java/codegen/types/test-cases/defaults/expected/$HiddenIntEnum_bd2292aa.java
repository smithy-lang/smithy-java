package software.amazon.smithy.java.example.defaults.model;

import software.amazon.smithy.java.core.schema.IntEnumValue;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * Requires a default branch in switches over enum variant types.
 * This keeps switches valid when the model adds values.
 */
final class $HiddenIntEnum_bd2292aa extends IntEnumValue implements NestedIntEnum {
    private $HiddenIntEnum_bd2292aa() {
        super(0);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        throw new UnsupportedOperationException();
    }
}
