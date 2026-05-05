/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rpcv2;

import java.util.Objects;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

/**
 * Client protocol implementation for {@code smithy.protocols#rpcv2Cbor}.
 */
public final class RpcV2CborProtocol extends AbstractRpcV2ClientProtocol {
    private static final String PAYLOAD_MEDIA_TYPE = "application/cbor";
    private static final Codec CBOR_CODEC = Rpcv2CborCodec.builder().build();

    public RpcV2CborProtocol(ShapeId service) {
        super(Rpcv2CborTrait.ID, service, PAYLOAD_MEDIA_TYPE);
    }

    @Override
    protected Codec codec() {
        return CBOR_CODEC;
    }

    @Override
    protected void customizeRequestBuilder(ModifiableHttpRequest builder) {
        builder.setHttpVersion(HttpVersion.HTTP_2);
    }

    public static final class Factory implements ClientProtocolFactory<Rpcv2CborTrait> {
        @Override
        public ShapeId id() {
            return Rpcv2CborTrait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, Rpcv2CborTrait trait) {
            return new RpcV2CborProtocol(
                    Objects.requireNonNull(settings.service(), "service is a required protocol setting"));
        }
    }
}
