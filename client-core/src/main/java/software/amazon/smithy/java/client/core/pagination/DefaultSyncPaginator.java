/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import java.util.Iterator;
import java.util.Objects;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;

final class DefaultSyncPaginator<I extends SerializableStruct, O extends SerializableStruct> implements Paginator<O> {

    private final Paginatable<I, O> call;

    // Token members
    private String nextToken = null;

    private final PaginationInputFactory<I> inputFactory;
    private final PaginationTokenExtractor extractor;


    // Page size parameters
    private int pageSize;
    private int totalMaxItems = 0;

    // Request override for paginated requests
    private RequestOverrideConfig overrideConfig = null;

    DefaultSyncPaginator(I input, ApiOperation<I, O> operation, Paginatable<I, O> call) {
        this.call = call;
        var trait = operation.schema().expectTrait(TraitKey.PAGINATED_TRAIT);
        // Input and output token paths are expected to be set.
        var inputTokenMember = trait.getInputToken().orElseThrow();
        var outputTokenPath = trait.getOutputToken().orElseThrow();

        // Page size and Items are optional
        var pageSizeMember = trait.getPageSize().orElse(null);
        var itemsPath = trait.getItems().orElse(null);

        this.inputFactory = new PaginationInputFactory<>(
                input,
                operation,
                inputTokenMember,
                pageSizeMember
        );

        if (pageSizeMember != null) {
            var pageSizeSchema = input.schema().member(pageSizeMember);
            pageSize = input.getMemberValue(pageSizeSchema);
        }

        this.extractor = new PaginationTokenExtractor(
                operation.outputSchema(),
                outputTokenPath,
                itemsPath
        );
    }

    @Override
    public void maxItems(int maxItems) {
        this.totalMaxItems = maxItems;
    }

    @Override
    public void overrideConfig(RequestOverrideConfig overrideConfig) {
        this.overrideConfig = overrideConfig;
    }

    @Override
    public Iterator<O> iterator() {
        return new Iterator<>() {
            // Start by assuming there is a next when instantiated.
            private boolean hasNext = true;
            private int remaining = totalMaxItems;
            private int maxItems = pageSize;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public O next() {
                // If there are fewer items allowed than we will request, reduce page size to match remaining.
                if (remaining > 0 && maxItems > remaining) {
                    maxItems = remaining;
                }

                // Get a new version of the original input with the new token and max value injected.
                var input = inputFactory.create(nextToken, maxItems);
                var output = call.call(input, overrideConfig);
                var res = extractor.extract(output);

                // If we see the same pagination token twice then stop pagination.
                if (nextToken != null && Objects.equals(nextToken, res.token())) {
                    hasNext = false;
                    return output;
                }

                // Update based on output values
                nextToken = res.token();
                remaining -= res.totalItems();

                // Next token is null or max results reached, indicating there are no more values.
                if (nextToken == null || (totalMaxItems != 0 && remaining == 0)) {
                    hasNext = false;
                }

                return output;
            }
        };
    }
}
