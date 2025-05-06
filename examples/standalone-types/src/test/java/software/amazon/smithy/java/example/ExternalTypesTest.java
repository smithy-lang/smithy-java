package software.amazon.smithy.java.example;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.example.standalone.model.HumanValidationException;
import software.amazon.smithy.java.framework.model.ValidationException;

class ExternalTypesTest {

    @Test
    void verifySchemasAreNotRegenerated() {
        HumanValidationException.builder().cause(ValidationException.builder().message("Test").build()).build().serializeMembers(new SpecificShapeSerializer() {

            @Override
            public void writeStruct(Schema schema, SerializableStruct struct) {
               schema.assertMemberTargetIs(ValidationException.$SCHEMA);
            }
        });
    }
}
