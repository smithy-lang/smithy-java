/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Converts exceptions so that they adhere to client-specific requirements.
 *
 * <p>This plugin allows for:
 * <ul>
 *     <li>Converting specific exceptions by class equality to another exception using
 *     {@link Builder#convert(Class, Function)}.</li>
 *     <li>Throwing exceptions as-is if they extend from a specific exception using
 *     {@link Builder#baseApiExceptionMapper(Class, Function)}.</li>
 *     <li>Changing {@link CallException}s that don't extend from a specific ApiException into the desired
 *     ApiException subtype using {@link Builder#baseApiExceptionMapper(Class, Function)}.</li>
 *     <li>Converting all other totally unknown exceptions into another kind of exception type using
 *     {@link Builder#rootExceptionMapper}</li>
 * </ul>
 *
 * <pre>{@code
 * ExceptionMapper mapper = ExceptionMapper.builder()
 *     .convert(SomeSpecificError.class, e -> new OtherError(e.getMessage(), e))
 *     .baseApiExceptionMapper(MyError.class, e -> {
 *         return switch(e.getFault()) {
 *             case CLIENT -> new MyClientError(e);
 *             case SERVER -> new MyServerError(e);
 *             default -> new MyError(e);
 *         }
 *     })
 *     .rootExceptionMapper(e -> new MyClientError(e))
 *     .build();
 * }</pre>
 */
public final class ExceptionMapper implements ClientPlugin {

    private final Map<Class<? extends RuntimeException>,
            Function<RuntimeException, RuntimeException>> mappers = new HashMap<>();
    private final Class<? extends CallException> baseApiExceptionType;
    private final Function<CallException, ? extends CallException> baseApiExceptionMapper;
    private final Function<RuntimeException, ? extends RuntimeException> rootExceptionMapper;

    private ExceptionMapper(Builder builder) {
        this.mappers.putAll(builder.mappers);
        this.baseApiExceptionType = builder.baseApiExceptionType;
        this.baseApiExceptionMapper = builder.baseApiExceptionMapper;
        this.rootExceptionMapper = builder.rootExceptionMapper;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.addInterceptor(new ExceptionInterceptor());
    }

    private final class ExceptionInterceptor implements ClientInterceptor {
        @Override
        public <O extends SerializableStruct> O modifyBeforeCompletion(
                OutputHook<?, O, ?, ?> hook,
                RuntimeException error
        ) {
            if (error == null) {
                return hook.output();
            }

            // Perform specific error conversions.
            var mapper = mappers.get(error.getClass());
            if (mapper != null) {
                throw mapper.apply(error);
            }

            // Perform base API error conversions for unknown ApiExceptions.
            if (baseApiExceptionType != null && error instanceof CallException a) {
                throw baseApiExceptionType.isInstance(error) ? error : baseApiExceptionMapper.apply(a);
            }

            throw rootExceptionMapper.apply(error);
        }
    }

    /**
     * Builds up an exception mapper.
     */
    public static final class Builder {
        private final Map<Class<? extends RuntimeException>,
                Function<RuntimeException, RuntimeException>> mappers = new HashMap<>();
        private Class<? extends CallException> baseApiExceptionType;
        private Function<CallException, ? extends CallException> baseApiExceptionMapper;
        private Function<RuntimeException, ? extends RuntimeException> rootExceptionMapper = Function.identity();

        public ExceptionMapper build() {
            return new ExceptionMapper(this);
        }

        /**
         * Converts a specific error to another kind of error.
         *
         * <p>These explicit conversions are check first before applying {@link #baseApiExceptionMapper} or
         * {@link #rootExceptionMapper}.
         *
         * @param exception Exception to convert.
         * @param mapper The mapper used to create the converted exception.
         * @return the builder.
         * @param <C> The exception to convert.
         */
        @SuppressWarnings("unchecked")
        public <C extends RuntimeException> Builder convert(Class<C> exception, Function<C, RuntimeException> mapper) {
            mappers.put(exception, (Function<RuntimeException, RuntimeException>) mapper);
            return this;
        }

        /**
         * Converts instances of {@link CallException} that don't implement the given {@code baseType} into an exception
         * that does extend from it using the given {@code mapper} function.
         *
         * <p>Setting a baseApiExceptionMapper is optional. This allows clients to create a base exception type for
         * all API-level exceptions.
         *
         * @param baseType The base type ApiExceptions should extend from.
         * @param mapper A mapper used to convert the found exception into a subtype of {@code baseType}.
         * @return the builder.
         * @param <C> the base type.
         */
        public <C extends CallException> Builder baseApiExceptionMapper(
                Class<C> baseType,
                Function<CallException, ? extends C> mapper
        ) {
            this.baseApiExceptionType = baseType;
            this.baseApiExceptionMapper = mapper;
            return this;
        }

        /**
         * Overrides the root exception mapper, used to convert completely unknown errors that don't extend from
         * {@link CallException}.
         *
         * <p>By default, the root exception mapper simply throws the given exception as-is.
         *
         * @param mapper The root-level exception mapper used to convert the given error or return it as-is.
         * @return the builder.
         */
        public Builder rootExceptionMapper(Function<RuntimeException, ? extends RuntimeException> mapper) {
            this.rootExceptionMapper = Objects.requireNonNull(mapper, "rootExceptionMapper cannot be null");
            return this;
        }
    }
}
