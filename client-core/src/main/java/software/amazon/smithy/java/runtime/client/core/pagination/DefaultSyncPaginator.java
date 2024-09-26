/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination;

import java.util.Iterator;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.PaginatedTrait;

final class DefaultSyncPaginator<I extends SerializableStruct, O extends SerializableStruct> implements Paginator<O> {

    private final Document inputDocument;
    private final Paginatable<I, O> call;
    private final ApiOperation<I, O> operation;

    // Token members
    private final String inputTokenMember;
    private final String outputTokenPath;
    private String nextToken = null;

    // Page size parameters
    private final String itemsPath;
    private final String pageSizeMember;
    private int pageSize;
    private int totalMaxItems = 0;

    // Request override for paginated requests
    private RequestOverrideConfig overrideConfig = null;

    DefaultSyncPaginator(SerializableStruct input, ApiOperation<I, O> operation, Paginatable<I, O> call) {
        this.inputDocument = Document.createTyped(input);
        this.call = call;
        this.operation = operation;
        var trait = operation.schema().expectTrait(PaginatedTrait.class);
        this.inputTokenMember = trait.getInputToken().orElseThrow();
        this.outputTokenPath = trait.getOutputToken().orElseThrow();
        this.itemsPath = trait.getItems().orElse(null);
        this.pageSizeMember = trait.getPageSize().orElse(null);
        if (pageSizeMember != null) {
            pageSize = inputDocument.getMember(pageSizeMember).asNumber().intValue();
        }
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

                // Deserialize a new version of the original input with the new token and max value set.
                var deserializer = new PaginationInjectingDeserializer(
                    inputDocument,
                    inputTokenMember,
                    nextToken,
                    pageSizeMember,
                    maxItems
                );
                var input = operation.inputBuilder().deserialize(deserializer).build();

                var output = call.call(input, overrideConfig);
                var serializer = new PaginationDiscoveringSerializer(outputTokenPath, itemsPath);
                output.serialize(serializer);
                serializer.flush();

                // Update based on output values
                nextToken = serializer.outputToken();
                remaining -= serializer.totalItems();

                // Next token is null or max results reached, indicating there are no more values.
                if (nextToken == null || (totalMaxItems != 0 && remaining == 0)) {
                    hasNext = false;
                }

                return output;
            }
        };
    }
}
