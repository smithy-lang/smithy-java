/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.coral.smithy.java.server.protocols.jsonrpc2;

import java.util.List;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServerProtocolProvider;
import software.amazon.smithy.model.shapes.ShapeId;

public final class JsonRpc2ProtocolProvider implements ServerProtocolProvider {
    @Override
    public ServerProtocol provideProtocolHandler(List<Service> candidateServices) {
        return new JsonRpc2Protocol(candidateServices);
    }

    @Override
    public ShapeId getProtocolId() {
        return JsonRpc2Protocol.JSON_RPC2_PROTOCOL_ID;
    }

    @Override
    public int priority() {
        return 1;
    }
}
