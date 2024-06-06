/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.http.api.ContentStream;

public final class ContentStreamAdapter implements ContentStream {

    private final DataStream delegate;

    public ContentStreamAdapter(DataStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public Flow.Publisher<ByteBuffer> publisher() {
        return delegate;
    }

    // TODO: Too hacky?
    DataStream delegate() {
        return delegate;
    }
}
