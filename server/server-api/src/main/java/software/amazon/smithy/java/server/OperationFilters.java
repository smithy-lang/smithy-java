/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

public final class OperationFilters {

    private OperationFilters() {}

    /**
     * Creates a {@link Predicate} that only includes operations with names in the provided set.
     *
     * @param operationNames Set of operation names to include
     * @return A {@link Predicate} that only includes the specified operations
     */
    public static Predicate<ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>> allowList(
            Set<String> operationNames
    ) {
        return new OperationFilters.OperationNameFilter(operationNames, null);
    }

    /**
     * Creates a {@link Predicate} that excludes operations with names in the provided set.
     *
     * @param operationNames Set of operation names to exclude
     * @return A {@link Predicate} that excludes the specified operations
     */
    static Predicate<ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>> blockList(
            Set<String> operationNames
    ) {
        return new OperationNameFilter(null, operationNames);
    }

    static final class OperationNameFilter implements Predicate<ApiOperation<?, ?>> {
        private final Set<String> allowlist;
        private final Set<String> blocklist;

        OperationNameFilter(Set<String> allowlist, Set<String> blocklist) {
            this.allowlist = allowlist != null ? allowlist : Collections.emptySet();
            this.blocklist = blocklist != null ? blocklist : Collections.emptySet();
        }

        @Override
        public boolean test(ApiOperation<?, ?> operation) {
            var operationName = operation.schema().id().getName();

            if (blocklist.contains(operationName)) {
                return false;
            }

            return allowlist.isEmpty() || allowlist.contains(operationName);
        }
    }
}
