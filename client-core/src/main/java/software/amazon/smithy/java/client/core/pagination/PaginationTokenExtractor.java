package software.amazon.smithy.java.client.core.pagination;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;

final class PaginationTokenExtractor {
    private final List<Schema> tokenPathSchemas;
    private final List<Schema> itemsPathSchemas;

    PaginationTokenExtractor(Schema outputSchema, String tokenPath, String itemPath) {
        this.tokenPathSchemas = getPathSchemas(outputSchema, tokenPath);
        this.itemsPathSchemas = getPathSchemas(outputSchema, itemPath);
    }

    private static List<Schema> getPathSchemas(Schema outputSchema, String path) {
        List<Schema> pathSchemas = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return pathSchemas;
        }
        Schema currentSchema = outputSchema;
        for (var component : path.split("\\.")) {
            currentSchema = currentSchema.member(component);
            pathSchemas.add(currentSchema);
        }
        return pathSchemas;
    }

    <O extends SerializableStruct> Result extract(O outputShape) {
        String token = getValueForPath(tokenPathSchemas, outputShape);
        var items = getValueForPath(itemsPathSchemas, outputShape);
        int totalItems = 0;
        if (items != null) {
            if (items instanceof Collection<?> ic) {
                totalItems = ic.size();
            } else if (items instanceof Map<?, ?> im) {
                totalItems = im.size();
            }
        }
        return new Result(token, totalItems);
    }

    private static <T, O extends SerializableStruct> T getValueForPath(List<Schema> schemaPath, O outputShape) {
        SerializableStruct shape = outputShape;
        var iter = schemaPath.iterator();
        while (iter.hasNext()) {
            var schema = iter.next();
            if (iter.hasNext()) {
                shape = shape.getMemberValue(schema);
            } else {
                return shape.getMemberValue(schema);
            }
        }
        return null;
    }

    record Result(String token, int totalItems) {}
}
