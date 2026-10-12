/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

final class FuzzedSource implements SigV4CaseGenerator.Source {
    private final FuzzedDataProvider data;

    FuzzedSource(FuzzedDataProvider data) {
        this.data = data;
    }

    @Override
    public int nextInt(int bound) {
        return data.consumeInt(0, bound - 1);
    }

    @Override
    public boolean nextBoolean() {
        return data.consumeBoolean();
    }

    @Override
    public long nextLong(long bound) {
        return data.consumeLong(0, bound - 1);
    }

    @Override
    public byte[] nextBytes(int maxLength) {
        return data.consumeBytes(data.consumeInt(0, maxLength));
    }
}
