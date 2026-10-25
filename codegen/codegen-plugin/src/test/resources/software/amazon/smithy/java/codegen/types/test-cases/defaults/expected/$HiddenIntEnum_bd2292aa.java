package software.amazon.smithy.java.example.defaults.model;

import software.amazon.smithy.java.core.schema.IntEnumValue;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * A permitted but unnameable variant of every enum generated with this package: a switch over
 * the variant types of an enum always needs a default branch, so it keeps compiling when the
 * model adds a value. Never instantiated.
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
