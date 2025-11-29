/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.InputStream;

final class InputStreamDataStream implements DataStream {

    private final InputStream inputStream;
    private final String contentType;
    private final long contentLength;
    private boolean consumed;

    InputStreamDataStream(InputStream inputStream, String contentType, long contentLength) {
        this.inputStream = inputStream;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public InputStream asInputStream() {
        if (consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }
        consumed = true;
        return inputStream;
    }

    @Override
    public boolean isReplayable() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return !consumed;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }
}
