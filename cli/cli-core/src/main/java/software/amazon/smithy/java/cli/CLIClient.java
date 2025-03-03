/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generic Client class used to mke CLI calls.
 *
 * <p>This client ONLY supports synchronous calls.
 *
 * <p><strong>NOTE:</strong> This class is used by code-generated CLI code and should not be
 * used directly by users.
 */
@SmithyInternalApi
public final class CLIClient extends Client {

    private CLIClient(Client.Builder<?, ?> builder) {
        super(builder);
    }

    /**
     * Execute an operation using this client.
     *
     * @param input Input shape
     * @param operation API Operation model
     * @return Operation output.
     * @param <I> Input shape type.
     * @param <O> Output shape type.
     */
    public <I extends SerializableStruct, O extends SerializableStruct> O call(
            I input,
            ApiOperation<I, O> operation
    ) {
        return call(input, operation, null).join();
    }

    /**
     * @return new {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Static builder for {@link CLIClient}.
     */
    public static final class Builder extends Client.Builder<CLIClient, Builder> {
        private Builder() {}

        @Override
        public CLIClient build() {
            return new CLIClient(this);
        }
    }
}
