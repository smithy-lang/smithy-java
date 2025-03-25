/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.iostream;

import software.amazon.smithy.java.server.ServerBuilder;
import software.amazon.smithy.java.server.ServerProvider;

public final class IOStreamServerProvider implements ServerProvider {
    @Override
    public String name() {
        return "smithy-io-server";
    }

    @Override
    public ServerBuilder<IOStreamServerBuilder> serverBuilder() {
        return new IOStreamServerBuilder();
    }

    @Override
    public int priority() {
        return 0;
    }
}
