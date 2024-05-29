package software.amazon.smithy.java.codegen.integrations.nullableannotation;


import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.sections.NullableAnnotationSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Adds a Nullable annotation section to returns/parameters of method signatures where specified as nullable
 *
 * <p>This interceptor will add provided Nullable annotation to the return and parameter method signatures
 */
final class NullableAnnotationInjectorInterceptor implements CodeInterceptor.Prepender<CodeSection, JavaWriter> {
    private final String nullableAnnotation;
    public NullableAnnotationInjectorInterceptor(String nullableAnnotation) {
        this.nullableAnnotation = nullableAnnotation;
    }

    @Override
    public Class<CodeSection> sectionType() {
        return CodeSection.class;
    }

    @Override
    public boolean isIntercepted(CodeSection section) {
        return section instanceof NullableAnnotationSection;
    }


    @Override
    public void prepend(JavaWriter writer, CodeSection section) {
        Shape shape;
        if (section instanceof NullableAnnotationSection ns) {
            shape = ns.shape();
        } else {
            throw new IllegalArgumentException("Nullable Annotation cannot be injected for section: " + section);
        }

        if (CodegenUtils.isNullableMember(MemberShape.builder().target(shape.toShapeId()).build())) {
            writer.pushState();
            writer.write("@${?nullable}$N${/nullable}${^nullable}$T");
            writer.popState();
        }

    }
}