/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ServiceLoader;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

public final class Rpcv2CborCodec implements Codec {
    private static final CborSerdeProvider PROVIDER;

    static {
        final String preferredName = System.getProperty("smithy-java.cbor-provider");
        CborSerdeProvider selected = null;
        for (var provider : ServiceLoader.load(CborSerdeProvider.class)) {
            if (preferredName != null) {
                if (provider.getName().equals(preferredName)) {
                    selected = provider;
                    break;
                }
            }
            if (selected == null) {
                selected = provider;
            } else if (provider.getPriority() > selected.getPriority()) {
                selected = provider;
            }
        }
        if (selected == null) {
            selected = new DefaultCborSerdeProvider();
        }
        PROVIDER = selected;
    }

    private Rpcv2CborCodec() {

    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getMediaType() {
        return "application/cbor";
    }

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        return PROVIDER.newSerializer(sink, new Rpcv2CborCodec.Settings());
    }

    @Override
    public ShapeDeserializer createDeserializer(byte[] source) {
        return PROVIDER.newDeserializer(source, new Rpcv2CborCodec.Settings());
    }

    @Override
    public ShapeDeserializer createDeserializer(ByteBuffer source) {
        return PROVIDER.newDeserializer(source, new Rpcv2CborCodec.Settings());
    }

    public record Settings() {}

    public static final class Builder {
        public Rpcv2CborCodec build() {
            return new Rpcv2CborCodec();
        }
    }
}
