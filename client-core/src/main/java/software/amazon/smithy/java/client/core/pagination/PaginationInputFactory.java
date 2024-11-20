package software.amazon.smithy.java.client.core.pagination;

import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Replaces values of a top-level structure members with values for pagination.
 *
 * @param <I> Input shape type for paginated operation.
 */
final class PaginationInputFactory<I extends SerializableStruct> {
    private final I input;
    private final ApiOperation<I, ?> operation;
    private final Schema inputTokenSchema;
    private final Schema maxResultsSchema;

    PaginationInputFactory(
            I input,
            ApiOperation<I, ?> operation,
            String inputTokenMember,
            String maxResultsMember
    ) {
        this.input = input;
        this.operation = operation;
        this.inputTokenSchema = input.schema().member(inputTokenMember);
        this.maxResultsSchema = maxResultsMember != null ? input.schema().member(maxResultsMember) : null;
    }

    I create(String token, Integer maxResults) {
        var builder = operation.inputBuilder();
        SchemaUtils.copyShape(input, builder);
        if (token != null) {
            builder.setMemberValue(inputTokenSchema, token);
        }
        if (maxResults != null) {
            builder.setMemberValue(maxResultsSchema, maxResults);
        }
        return builder.build();
    }
}
