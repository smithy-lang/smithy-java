/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rpcv2json;

import java.util.Objects;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.rpcv2.AbstractRpcV2ClientProtocol;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2JsonTrait;

/**
 * Client protocol implementation for {@code smithy.protocols#rpcv2Json}.
 *
 * <p>BigDecimal and BigInteger values are serialized as JSON strings to preserve
 * arbitrary precision.
 */
public final class RpcV2JsonProtocol extends AbstractRpcV2ClientProtocol {
    private static final String PAYLOAD_MEDIA_TYPE = "application/json";

    private final JsonCodec codec;

    public RpcV2JsonProtocol(ShapeId service) {
        super(Rpcv2JsonTrait.ID, service, PAYLOAD_MEDIA_TYPE);
        this.codec = JsonCodec.builder()
                .defaultNamespace(service.getNamespace())
                .useStringForArbitraryPrecision(true)
                .build();
    }

    @Override
    protected Codec codec() {
        return codec;
    }

    public static final class Factory implements ClientProtocolFactory<Rpcv2JsonTrait> {
        @Override
        public ShapeId id() {
            return Rpcv2JsonTrait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, Rpcv2JsonTrait trait) {
            return new RpcV2JsonProtocol(
                    Objects.requireNonNull(settings.service(), "service is a required protocol setting"));
        }
    }
}
