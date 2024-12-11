/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.util.Objects;
import java.util.ServiceLoader;
import software.amazon.smithy.java.core.serde.TimestampFormatter;

/**
 * Settings used by a {@link JsonCodec}.
 */
public final class JsonSettings {

    private static final JsonSerdeProvider PROVIDER;

    static {
        final String preferredName = System.getProperty("smithy-java.json-provider");
        JsonSerdeProvider selected = null;
        for (JsonSerdeProvider provider : ServiceLoader.load(JsonSerdeProvider.class)) {
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
            throw new IllegalStateException("At least one JSON provider should be registered.");
        }
        PROVIDER = selected;
    }

    private final TimestampResolver timestampResolver;
    private final JsonFieldMapper fieldMapper;
    private final boolean forbidUnknownUnionMembers;
    private final String defaultNamespace;
    private final JsonSerdeProvider provider;

    private JsonSettings(Builder builder) {
        this.timestampResolver = builder.useTimestampFormat
            ? new TimestampResolver.UseTimestampFormatTrait(builder.defaultTimestampFormat)
            : new TimestampResolver.StaticFormat(builder.defaultTimestampFormat);
        this.fieldMapper = builder.useJsonName
            ? new JsonFieldMapper.UseJsonNameTrait()
            : JsonFieldMapper.UseMemberName.INSTANCE;
        this.forbidUnknownUnionMembers = builder.forbidUnknownUnionMembers;
        this.defaultNamespace = builder.defaultNamespace;
        this.provider = builder.provider;
    }

    /**
     * Create a settings builder.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The timestamp resolver used to determine the timestamp format of a timestamp.
     *
     * @return the timestamp resolver.
     */
    public TimestampResolver timestampResolver() {
        return timestampResolver;
    }

    /**
     * The field mapper used to identify JSON fields (e.g., use member names, use the jsonName trait, etc.).
     *
     * @return the field mapper.
     */
    public JsonFieldMapper fieldMapper() {
        return fieldMapper;
    }

    /**
     * Whether unknown union members should fail deserialization.
     *
     * @return true if unknown union members are forbidden.
     */
    public boolean forbidUnknownUnionMembers() {
        return forbidUnknownUnionMembers;
    }

    /**
     * The default namespace to use when attempting to deserialize documents into types and the document discriminator
     * uses a relative ID.
     *
     * @return the default namespace or null.
     */
    public String defaultNamespace() {
        return defaultNamespace;
    }

    JsonSerdeProvider provider() {
        return provider;
    }

    void updateBuilder(Builder builder) {
        builder.forbidUnknownUnionMembers(forbidUnknownUnionMembers);
        builder.defaultNamespace(defaultNamespace);
        builder.overrideSerdeProvider(provider);
        if (timestampResolver instanceof TimestampResolver.UseTimestampFormatTrait) {
            builder.useTimestampFormat(true);
        }
        if (fieldMapper instanceof JsonFieldMapper.UseJsonNameTrait) {
            builder.useJsonName(true);
        }
    }

    /**
     * Convert the settings object to a builder.
     *
     * @return the builder.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        updateBuilder(builder);
        return builder;
    }

    /**
     * Creates a JsonSettings object.
     */
    public static final class Builder {
        private boolean useJsonName;
        private boolean useTimestampFormat = false;
        private TimestampFormatter defaultTimestampFormat = TimestampFormatter.Prelude.EPOCH_SECONDS;
        private boolean forbidUnknownUnionMembers;
        private String defaultNamespace;
        private JsonSerdeProvider provider = PROVIDER;

        private Builder() {}

        /**
         * Create the JsonSettings object.
         *
         * @return the created JsonSettings.
         */
        public JsonSettings build() {
            return new JsonSettings(this);
        }

        /**
         * Whether to use the jsonName trait or just the member name.
         *
         * <p>The jsonName trait is ignored by default.
         *
         * @param useJsonName True to use the jsonName trait.
         * @return the builder.
         */
        public Builder useJsonName(boolean useJsonName) {
            this.useJsonName = useJsonName;
            return this;
        }

        /**
         * Whether to use the timestampFormat trait or ignore it.
         *
         * <p>The timestampFormat trait is ignored by default.
         *
         * @param useTimestampFormat True to honor the timestampFormat trait.
         * @return the builder.
         */
        public Builder useTimestampFormat(boolean useTimestampFormat) {
            this.useTimestampFormat = useTimestampFormat;
            return this;
        }

        /**
         * The default timestamp format to assume for timestamp values.
         *
         * <p>Assumes "epoch-seconds" by default.
         *
         * @param defaultTimestampFormat The default timestamp format to assume.
         * @return the builder.
         */
        public Builder defaultTimestampFormat(TimestampFormatter defaultTimestampFormat) {
            this.defaultTimestampFormat = Objects.requireNonNull(defaultTimestampFormat);
            return this;
        }

        /**
         * Whether to forbid or ignore unknown union members.
         *
         * <p>Unknown union members are ignored by default.
         *
         * @param forbid True to forbid unknown union members.
         * @return the builder.
         */
        public Builder forbidUnknownUnionMembers(boolean forbid) {
            this.forbidUnknownUnionMembers = forbid;
            return this;
        }

        /**
         * Sets the default namespace when attempting to deserialize documents that use a relative shape ID.
         *
         * <p>No default namespace is used unless one is explicitly provided.
         *
         * @param defaultNamespace Default namespace to set.
         * @return the builder.
         */
        public Builder defaultNamespace(String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            return this;
        }

        /**
         * Uses a custom JSON serde provider.
         *
         * @param provider the JSON serde provider to use.
         * @return the builder.
         */
        Builder overrideSerdeProvider(JsonSerdeProvider provider) {
            this.provider = Objects.requireNonNull(provider);
            return this;
        }
    }
}
