/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;

/** A discoverable, available {@link TlsProvider} used to test ServiceLoader-based selection. */
public final class AvailableTestTlsProvider implements TlsProvider {
    @Override
    public ConnectionTransport connect(TlsConnectionContext connection) throws IOException {
        throw new IOException("test provider does not connect");
    }
}
