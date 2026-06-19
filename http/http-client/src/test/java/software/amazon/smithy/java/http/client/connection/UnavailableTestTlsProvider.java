/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;

/** A discoverable {@link TlsProvider} that reports unavailable, to test the availability guard. */
public final class UnavailableTestTlsProvider implements TlsProvider {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ConnectionTransport connect(TlsConnectionContext connection) throws IOException {
        throw new IOException("unavailable");
    }
}
