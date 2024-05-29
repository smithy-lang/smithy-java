package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains a getter method.
 *
 * @param shape shape to add nullable annotation
 */
public record NullableAnnotationSection(Shape shape) implements CodeSection {}

