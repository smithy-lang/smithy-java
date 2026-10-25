package software.amazon.smithy.java.example.defaults.model;

import software.amazon.smithy.java.core.schema.EnumValue;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * A permitted but unnameable variant of every enum generated with this package: a switch over
 * the variant types of an enum always needs a default branch, so it keeps compiling when the
 * model adds a value. Never instantiated.
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
