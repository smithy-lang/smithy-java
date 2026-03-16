/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.rpcv2;

import java.util.List;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

/**
 * Server protocol implementation for {@code smithy.protocols#rpcv2Cbor}.
 */
final class RpcV2CborProtocol extends AbstractRpcV2ServerProtocol {
    private static final String PAYLOAD_MEDIA_TYPE = "application/cbor";
    private static final Rpcv2CborCodec CODEC = Rpcv2CborCodec.builder().build();

    RpcV2CborProtocol(List<Service> services) {
        super(services, PAYLOAD_MEDIA_TYPE, true);
    }

    @Override
    public ShapeId getProtocolId() {
        return Rpcv2CborTrait.ID;
    }

    @Override
    protected Codec codec() {
        return CODEC;
    }
}
