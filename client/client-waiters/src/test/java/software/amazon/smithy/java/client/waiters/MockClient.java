/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.waiters.models.GetFoosInput;
import software.amazon.smithy.java.client.waiters.models.GetFoosOutput;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;

final class MockClient {
    private final String expectedId;
    private final Iterator<? extends SerializableStruct> iterator;

    MockClient(String expectedId, List<? extends SerializableStruct> outputs) {
        this.expectedId = expectedId;
        this.iterator = outputs.iterator();
    }

    public GetFoosOutput getFoosSync(GetFoosInput in, RequestOverrideConfig override) {
        if (!Objects.equals(expectedId, in.id())) {
            throw new IllegalArgumentException("ID: " + in.id() + " does not match expected " + expectedId);
        } else if (!iterator.hasNext()) {
            throw new IllegalArgumentException("No more requests expected but got: " + in);
        }
        var next = iterator.next();
        if (next instanceof GetFoosOutput output) {
            return output;
        } else if (next instanceof ModeledException exc) {
            // Throw exception
            throw exc;
        }

        throw new IllegalArgumentException("Expected an output shape or modeled exception. Found: " + next);
    }
}
