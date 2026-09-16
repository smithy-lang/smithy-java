/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Entry point from the Query protocols into generated serializers.
 *
 * <p>One registry per variant rather than one registry keyed by variant: the two variants disagree on
 * member names, list flattening, and whether maps are supported at all, so a generated class is only
 * ever valid for the variant that emitted it. The variant enum doubles as the settings identity,
 * which the registry compares by identity and an enum constant satisfies for free.
 *
 * <p>The action and the API version stay out of the generated class. They are per-request and
 * per-client respectively, while the cache key is the input shape, so baking them in would key a
 * class on data that does not belong to the shape.
 */
final class SmithyGeneratedQuerySerde {
    private static final RuntimeCodecRegistry<GeneratedQueryCodec> AWS_REGISTRY = new RuntimeCodecRegistry<>(
            new QueryRuntimeCodegenBackend(QueryFormSerializer.QueryVariant.AWS_QUERY));
    private static final RuntimeCodecRegistry<GeneratedQueryCodec> EC2_REGISTRY = new RuntimeCodecRegistry<>(
            new QueryRuntimeCodegenBackend(QueryFormSerializer.QueryVariant.EC2_QUERY));

    private static final boolean ENABLED =
            RuntimeCodegenFeature.available() && RuntimeCodegenFeature.enabled("awsquery");

    private SmithyGeneratedQuerySerde() {}

    /**
     * Serializes {@code input}, or returns null when the interpreted path must handle it.
     *
     * <p>Null covers both "generation is off" and "this shape cannot be generated"; the caller treats
     * them the same way.
     */
    static ByteBuffer serialize(
            QueryFormSerializer.QueryVariant variant,
            SerializableStruct input,
            String action,
            String version
    ) {
        return ENABLED ? serializeGenerated(variant, input, action, version) : null;
    }

    /**
     * Serializes with a generated codec regardless of {@code smithy-java.runtime-codegen}.
     *
     * <p>For the differential tests, which compare generated output against interpreted output in the
     * same JVM and so cannot switch either off. Still returns null for a shape the backend rejects.
     */
    static ByteBuffer serializeGenerated(
            QueryFormSerializer.QueryVariant variant,
            SerializableStruct input,
            String action,
            String version
    ) {
        GeneratedQueryCodec codec = registry(variant).get(input.schema(), variant);
        if (codec == null) {
            return null;
        }
        QueryFormWriter writer = QueryFormWriter.acquire(action, version);
        try {
            codec.write(input, writer);
            return writer.detach();
        } finally {
            QueryFormWriter.release(writer);
        }
    }

    private static RuntimeCodecRegistry<GeneratedQueryCodec> registry(QueryFormSerializer.QueryVariant variant) {
        return variant == QueryFormSerializer.QueryVariant.EC2_QUERY ? EC2_REGISTRY : AWS_REGISTRY;
    }
}
