package software.amazon.smithy.java.example;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.example.standalone.model.Human;
import software.amazon.smithy.java.framework.model.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIndexTest {

    @Test
    void basicTest() {
        var index = SchemaIndex.getCombinedSchemaIndex();
        assertThat(index.getSchema(Human.$ID)).isSameAs(Human.$SCHEMA);
    }

    @Test
    void externalTypes() {
        var index = SchemaIndex.getCombinedSchemaIndex();
        assertThat(index.getSchema(ValidationException.$ID)).isSameAs(ValidationException.$SCHEMA);

    }
}
