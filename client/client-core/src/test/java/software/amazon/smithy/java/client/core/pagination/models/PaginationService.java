/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination.models;

import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;

public final class PaginationService implements ApiService {
    @Override
    public Schema schema() {
        return Schema.createService(ShapeId.from("smithy.example#Pagination"));
    }
}
