package software.amazon.smithy.java.core.schema;

import java.util.List;

public interface CustomValidationRule {
  // takes in the schema and checks if the rules applies to it (using schema.getTrait and checking the trait this rule is being defined for is there)
  boolean appliesTo(Schema schema);

  List<ValidationError> validate(Schema schema, Object value, String path);
}
