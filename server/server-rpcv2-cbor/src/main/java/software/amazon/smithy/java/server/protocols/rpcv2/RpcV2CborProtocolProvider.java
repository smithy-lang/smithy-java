/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.rpcv2;

import java.util.List;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServerProtocolProvider;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

public final class RpcV2CborProtocolProvider implements ServerProtocolProvider {
    @Override
    public ServerProtocol provideProtocolHandler(List<Service> candidateServices) {
        return new RpcV2CborProtocol(candidateServices);
    }

    @Override
    public ShapeId getProtocolId() {
        return Rpcv2CborTrait.ID;
    }

    @Override
    public int priority() {
        return 1;
    }
}
