/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.rpcv2json;

import java.util.List;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.rpcv2.AbstractRpcV2ServerProtocol;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2JsonTrait;

/**
 * Server protocol implementation for {@code smithy.protocols#rpcv2Json}.
 *
 * <p>Uses JSON with string-encoded arbitrary precision numbers.
 */
final class RpcV2JsonProtocol extends AbstractRpcV2ServerProtocol {
    private static final String PAYLOAD_MEDIA_TYPE = "application/json";
    private static final JsonCodec CODEC = JsonCodec.builder()
            .useStringForArbitraryPrecision(true)
            .build();

    RpcV2JsonProtocol(List<Service> services) {
        super(services, PAYLOAD_MEDIA_TYPE);
    }

    @Override
    public ShapeId getProtocolId() {
        return Rpcv2JsonTrait.ID;
    }

    @Override
    protected Codec codec() {
        return CODEC;
    }
}
