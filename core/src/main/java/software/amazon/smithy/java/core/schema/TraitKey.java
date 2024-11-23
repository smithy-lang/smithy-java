/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HostLabelTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.IdempotentTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.ReadonlyTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.RetryableTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.XmlAttributeTrait;
import software.amazon.smithy.model.traits.XmlFlattenedTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Identity-based access to a specific trait used with methods like {@link Schema#getTrait}.
 *
 * @param <T> Trait to access with the key.
 */
public final class TraitKey<T extends Trait> {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ClassValue<TraitKey<? extends Trait>> KEY_POOL = new ClassValue<>() {
        @Override
        @SuppressWarnings("unchecked")
        protected TraitKey<?> computeValue(Class<?> clazz) {
            return new TraitKey<>((Class<Trait>) clazz, COUNTER.getAndIncrement());
        }
    };

    // Provide static instance variables for commonly used traits throughout smithy-java.
    // Note that TraitKeys can be accessed at any time through TraitKey#get; these are just pre-defined.

    public static final TraitKey<RequiredTrait> REQUIRED_TRAIT = TraitKey.get(RequiredTrait.class);
    public static final TraitKey<DefaultTrait> DEFAULT_TRAIT = TraitKey.get(DefaultTrait.class);
    public static final TraitKey<TimestampFormatTrait> TIMESTAMP_FORMAT_TRAIT = TraitKey.get(
        TimestampFormatTrait.class
    );
    public static final TraitKey<JsonNameTrait> JSON_NAME_TRAIT = TraitKey.get(JsonNameTrait.class);
    public static final TraitKey<SparseTrait> SPARSE_TRAIT = TraitKey.get(SparseTrait.class);
    public static final TraitKey<LengthTrait> LENGTH_TRAIT = TraitKey.get(LengthTrait.class);
    public static final TraitKey<RangeTrait> RANGE_TRAIT = TraitKey.get(RangeTrait.class);
    public static final TraitKey<PatternTrait> PATTERN_TRAIT = TraitKey.get(PatternTrait.class);
    public static final TraitKey<ErrorTrait> ERROR_TRAIT = get(ErrorTrait.class);
    public static final TraitKey<ReadonlyTrait> READ_ONLY_TRAIT = get(ReadonlyTrait.class);
    public static final TraitKey<IdempotentTrait> IDEMPOTENT_TRAIT = get(IdempotentTrait.class);
    public static final TraitKey<IdempotencyTokenTrait> IDEMPOTENCY_TOKEN = get(IdempotencyTokenTrait.class);
    public static final TraitKey<RetryableTrait> RETRYABLE_TRAIT = get(RetryableTrait.class);
    public static final TraitKey<EndpointTrait> ENDPOINT_TRAIT = TraitKey.get(EndpointTrait.class);
    public static final TraitKey<HostLabelTrait> HOST_LABEL_TRAIT = TraitKey.get(HostLabelTrait.class);
    public static final TraitKey<MediaTypeTrait> MEDIA_TYPE_TRAIT = TraitKey.get(MediaTypeTrait.class);
    @SuppressWarnings("deprecation")
    public static final TraitKey<EnumTrait> ENUM_TRAIT = TraitKey.get(EnumTrait.class);
    public static final TraitKey<HttpTrait> HTTP_TRAIT = TraitKey.get(HttpTrait.class);
    public static final TraitKey<HttpErrorTrait> HTTP_ERROR_TRAIT = TraitKey.get(HttpErrorTrait.class);
    public static final TraitKey<HttpHeaderTrait> HTTP_HEADER_TRAIT = TraitKey.get(HttpHeaderTrait.class);
    public static final TraitKey<HttpLabelTrait> HTTP_LABEL_TRAIT = TraitKey.get(HttpLabelTrait.class);
    public static final TraitKey<HttpQueryTrait> HTTP_QUERY_TRAIT = TraitKey.get(HttpQueryTrait.class);
    public static final TraitKey<HttpQueryParamsTrait> HTTP_QUERY_PARAMS_TRAIT = TraitKey.get(
        HttpQueryParamsTrait.class
    );
    public static final TraitKey<HttpPrefixHeadersTrait> HTTP_PREFIX_HEADERS_TRAIT = TraitKey.get(
        HttpPrefixHeadersTrait.class
    );
    public static final TraitKey<HttpPayloadTrait> HTTP_PAYLOAD_TRAIT = TraitKey.get(HttpPayloadTrait.class);
    public static final TraitKey<HttpResponseCodeTrait> HTTP_RESPONSE_CODE_TRAIT = TraitKey.get(
        HttpResponseCodeTrait.class
    );
    public static final TraitKey<StreamingTrait> STREAMING_TRAIT = TraitKey.get(StreamingTrait.class);
    public static final TraitKey<SensitiveTrait> SENSITIVE_TRAIT = TraitKey.get(SensitiveTrait.class);
    public static final TraitKey<XmlNameTrait> XML_NAME_TRAIT = TraitKey.get(XmlNameTrait.class);
    public static final TraitKey<XmlAttributeTrait> XML_ATTRIBUTE_TRAIT = TraitKey.get(XmlAttributeTrait.class);
    public static final TraitKey<XmlFlattenedTrait> XML_FLATTENED_TRAIT = TraitKey.get(XmlFlattenedTrait.class);
    public static final TraitKey<XmlNamespaceTrait> XML_NAMESPACE_TRAIT = TraitKey.get(XmlNamespaceTrait.class);

    private final Class<T> traitClass;
    final int id;

    /**
     * Gets the key for a trait for use with methods like {@link Schema#getTrait}.
     *
     * @param traitClass Trait to get the key of.
     * @return the key for the trait.
     * @param <T> Trait class.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Trait> TraitKey<T> get(Class<T> traitClass) {
        return (TraitKey<T>) KEY_POOL.get(traitClass);
    }

    private TraitKey(Class<T> traitClass, int id) {
        this.traitClass = traitClass;
        this.id = id;
    }

    /**
     * Get the class this trait key wraps.
     *
     * @return the class of the trait provided by this key.
     */
    public Class<T> traitClass() {
        return traitClass;
    }
}
