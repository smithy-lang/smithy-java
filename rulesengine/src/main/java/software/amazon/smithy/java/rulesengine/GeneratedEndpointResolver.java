/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointContext;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.endpoints.EndpointResolverParams;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsValidHostLabel;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Split;

/**
 * Runtime support for endpoint resolvers generated from rules-engine bytecode.
 *
 * @param <S> generated, field-based evaluation state
 */
public abstract class GeneratedEndpointResolver<S extends GeneratedEndpointResolver.EvaluationState>
        implements EndpointResolver {

    private final Bytecode bytecode;
    private final RulesFunction[] functions;
    private final RulesExtension[] extensions;
    private final Map<String, Function<Context, Object>> builtinProviders;
    private final ContextProvider ctxProvider = new ContextProvider.OrchestratingProvider();
    private final ThreadLocal<S> state = ThreadLocal.withInitial(this::createState);
    private volatile BytecodeEndpointResolver traceResolver;

    /**
     * Creates a generated resolver from the serialized program used to produce the subclass.
     *
     * @param program serialized rules-engine bytecode
     */
    protected GeneratedEndpointResolver(byte[] program) {
        var engine = new RulesEngineBuilder();
        this.bytecode = engine.load(program);
        this.functions = bytecode.getFunctions();
        this.extensions = engine.getExtensions().toArray(new RulesExtension[0]);
        this.builtinProviders = engine.getBuiltinProviders();
    }

    /**
     * Creates a generated resolver from a binary program resource.
     *
     * @param resourceOwner class used to load the resource
     * @param resourceName class-relative or absolute resource name
     */
    protected GeneratedEndpointResolver(Class<?> resourceOwner, String resourceName) {
        this(loadProgram(resourceOwner, resourceName));
    }

    private static byte[] loadProgram(Class<?> resourceOwner, String resourceName) {
        try (var stream = resourceOwner.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Endpoint BDD resource not found: " + resourceName + " from " + resourceOwner.getName());
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load endpoint BDD resource: " + resourceName, e);
        }
    }

    /**
     * Gets the compiled program that produced this resolver.
     *
     * @return the compiled bytecode
     */
    public final Bytecode getBytecode() {
        return bytecode;
    }

    @Override
    public final Endpoint resolveEndpoint(EndpointResolverParams params) {
        Context context = params.context();
        S state = this.state.get();
        boolean reusable = !state.inUse;
        if (!reusable) {
            state = createState();
        }
        state.inUse = true;
        try {
            initialize(state);
            ContextProvider.createEndpointParams(
                    state,
                    ctxProvider,
                    context,
                    params.operation(),
                    params.inputValue());
            state.setContext(context);
            finish(state, context);
            return evaluate(state, context);
        } finally {
            state.inUse = false;
        }
    }

    /**
     * Resolves an endpoint from a generated, field-based parameter object.
     *
     * @param context resolution context
     * @param parameters generated parameters created by this resolver
     * @return resolved endpoint
     */
    public final Endpoint resolveEndpoint(Context context, GeneratedParameters parameters) {
        S state = directState(parameters);
        if (state == null || !state.claim(Thread.currentThread()) || state.inUse) {
            state = this.state.get();
        }
        if (state.inUse) {
            state = createState();
        }
        state.inUse = true;
        try {
            if (state != parameters) {
                initialize(state, parameters);
            }
            state.setContext(context);
            finish(state, context);
            return evaluate(state, context);
        } finally {
            state.inUse = false;
        }
    }

    /**
     * Creates a generated parameter object with compile-time defaults applied.
     *
     * @return generated parameters
     */
    public abstract GeneratedParameters createParameters();

    /**
     * Returns the generated state embedded in a compatible parameter object.
     */
    protected S directState(GeneratedParameters parameters) {
        return null;
    }

    /**
     * Creates generated parameters and overlays the given values.
     *
     * @param values parameter values by rules-engine name
     * @return generated parameters
     */
    public final GeneratedParameters createParameters(Map<String, Object> values) {
        GeneratedParameters result = createParameters();
        result.putAll(values);
        return result;
    }

    /**
     * Creates the generated state used by one evaluation.
     */
    protected abstract S createState();

    /**
     * Clears generated fields and applies compile-time defaults.
     */
    protected abstract void initialize(S state);

    /**
     * Clears evaluation temporaries and directly loads generated parameters.
     */
    protected abstract void initialize(S state, GeneratedParameters parameters);

    /**
     * Applies builtins and validates required generated fields.
     */
    protected abstract void finish(S state, Context context);

    /**
     * Evaluates the generated BDD program.
     */
    protected abstract Endpoint evaluate(S state);

    private Endpoint evaluate(S state, Context context) {
        BddTraceSink sink = context.get(RulesEngineSettings.BDD_TRACE_SINK);
        if (sink == null) {
            return evaluate(state);
        }
        return traceResolver().resolveTraced(context, sink, state, () -> evaluate(state));
    }

    private BytecodeEndpointResolver traceResolver() {
        BytecodeEndpointResolver result = traceResolver;
        if (result == null) {
            synchronized (this) {
                result = traceResolver;
                if (result == null) {
                    result = new BytecodeEndpointResolver(bytecode, List.of(extensions), builtinProviders);
                    traceResolver = result;
                }
            }
        }
        return result;
    }

    /**
     * Gets a runtime-resolved function by its compiled index.
     */
    protected final RulesFunction function(int index) {
        return functions[index];
    }

    /**
     * Gets the endpoint extensions used when creating generated state.
     */
    protected final RulesExtension[] extensions() {
        return extensions;
    }

    /**
     * Resolves a builtin and its fallback default for a register.
     */
    protected final Object builtin(int register, Context context) {
        RegisterDefinition definition = bytecode.getRegisterDefinitions()[register];
        Object value;
        if (definition.builtinKey() != null) {
            value = context.get(definition.builtinKey());
        } else {
            Function<Context, Object> provider = builtinProviders.get(definition.builtin());
            if (provider == null) {
                throw new IllegalStateException("Missing builtin provider: " + definition.builtin());
            }
            value = provider.apply(context);
        }
        return value != null ? value : definition.defaultValue();
    }

    /**
     * A target for endpoint parameters extracted from operation inputs and context.
     */
    public interface ParameterSink {
        /**
         * Writes a parameter by its rules-engine name.
         */
        void put(String name, Object value);

        /**
         * Writes all parameters.
         */
        default void putAll(Map<String, Object> values) {
            for (var entry : values.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * A reusable, field-based parameter object emitted with each generated resolver.
     */
    public interface GeneratedParameters extends ParameterSink {
        /**
         * Copies explicitly set parameters to another sink.
         */
        void copyTo(ParameterSink sink);
    }

    /**
     * Runtime-only state shared by all generated field layouts.
     */
    public abstract static class EvaluationState implements ParameterSink {
        private static final java.lang.invoke.VarHandle OWNER;

        static {
            try {
                OWNER = java.lang.invoke.MethodHandles.lookup()
                        .findVarHandle(EvaluationState.class, "owner", Thread.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final RulesExtension[] extensions;
        private final RulesExtension[] generatedAuthExtensions;
        private final RulesExtension[] legacyPropertyExtensions;
        private final UriFactory uriFactory = new UriFactory();
        private volatile Thread owner;
        boolean inUse;
        private Context context;
        private Endpoint.Builder endpointBuilder = Endpoint.builder();
        private final GeneratedAuthSchemeProperties generatedAuthScheme = new GeneratedAuthSchemeProperties();

        /**
         * @param extensions endpoint extensions to apply to results
         */
        protected EvaluationState(RulesExtension[] extensions) {
            this.extensions = extensions;
            List<RulesExtension> generatedAuth = new java.util.ArrayList<>();
            List<RulesExtension> legacyProperties = new java.util.ArrayList<>();
            try {
                var authMethod = RulesExtension.class.getMethod(
                        "extractEndpointAuthScheme",
                        Endpoint.Builder.class,
                        Context.class,
                        PropertyGetter.class,
                        Map.class);
                var propertiesMethod = RulesExtension.class.getMethod(
                        "extractEndpointProperties",
                        Endpoint.Builder.class,
                        Context.class,
                        Map.class,
                        Map.class);
                for (RulesExtension extension : extensions) {
                    if (extension.getClass().getMethod(
                                    authMethod.getName(),
                                    authMethod.getParameterTypes())
                            .getDeclaringClass() != RulesExtension.class) {
                        generatedAuth.add(extension);
                    } else if (extension.getClass().getMethod(
                                    propertiesMethod.getName(),
                                    propertiesMethod.getParameterTypes())
                            .getDeclaringClass() != RulesExtension.class) {
                        legacyProperties.add(extension);
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to classify rules extensions", e);
            }
            this.generatedAuthExtensions = generatedAuth.toArray(RulesExtension[]::new);
            this.legacyPropertyExtensions = legacyProperties.toArray(RulesExtension[]::new);
        }

        /**
         * Copies explicitly set endpoint parameters to another sink.
         */
        public abstract void copyTo(ParameterSink sink);

        final void setContext(Context context) {
            this.context = context;
        }

        final boolean claim(Thread thread) {
            Thread current = owner;
            return current == thread || current == null && OWNER.compareAndSet(this, null, thread);
        }

        public final Object getProperty(Object target, String property) {
            return EndpointUtils.getProperty(target, property);
        }

        public final Object getIndex(Object target, int index) {
            return EndpointUtils.getIndex(target, index);
        }

        public final Object getNegativeIndex(Object target, int index) {
            return EndpointUtils.getNegativeIndex(target, index);
        }

        public final String substring(String value, int start, int end, boolean reverse) {
            return EndpointUtils.getSubstring(value, start, end, reverse);
        }

        public final boolean substringEquals(String value, int start, int end, boolean reverse, String expected) {
            return EndpointUtils.substringEquals(value, start, end, reverse, expected);
        }

        public final Object splitGet(String value, String delimiter, int index) {
            return EndpointUtils.splitGet(value, delimiter, index);
        }

        public final List<String> split(String value, String delimiter, int limit) {
            return Split.split(value, delimiter, limit);
        }

        public final boolean isValidHostLabel(String value, boolean allowDots) {
            return IsValidHostLabel.isValidHostLabel(value, allowDots);
        }

        public final SmithyUri parseUri(String value) {
            return uriFactory.createUri(value);
        }

        public final String uriEncode(String value) {
            return URLEncoding.encodeUnreserved(value, false);
        }

        public final SmithyUri buildUri(String scheme, String host, String path) {
            return SmithyUri.ofTrusted(scheme, host, -1, path, null);
        }

        public final Map<String, Object> authSchemeProperties(
                Object v0,
                String k0,
                Object v1,
                String k1,
                Object v2,
                String k2,
                Object v3,
                String k3
        ) {
            generatedAuthScheme.set(null, null, v0, k0, v1, k1, v2, k2, v3, k3);
            return generatedAuthScheme;
        }

        public final Map<String, Object> authSchemeProperties(
                Object extraValue,
                String extraKey,
                Object v0,
                String k0,
                Object v1,
                String k1,
                Object v2,
                String k2,
                Object v3,
                String k3
        ) {
            generatedAuthScheme.set(extraValue, extraKey, v0, k0, v1, k1, v2, k2, v3, k3);
            return generatedAuthScheme;
        }

        public final Endpoint endpoint(
                Object url,
                Map<String, Object> properties,
                Map<String, List<String>> headers
        ) {
            SmithyUri uri = url instanceof SmithyUri smithyUri
                    ? smithyUri
                    : uriFactory.createUri((String) url);
            if (headers.isEmpty()
                    && properties instanceof GeneratedAuthSchemeProperties authScheme
                    && generatedAuthExtensions.length == 1
                    && legacyPropertyExtensions.length == 0) {
                var directAuthScheme = generatedAuthExtensions[0]
                        .createEndpointAuthScheme(context, authScheme, headers);
                if (directAuthScheme != null) {
                    return Endpoint.create(uri, directAuthScheme);
                }
            }
            var builder = endpointBuilder.uri(uri);
            try {
                if (!headers.isEmpty()) {
                    builder.putProperty(EndpointContext.HEADERS, headers);
                }
                if (properties instanceof GeneratedAuthSchemeProperties authScheme) {
                    for (var extension : generatedAuthExtensions) {
                        extension.extractEndpointAuthScheme(builder, context, authScheme, headers);
                    }
                    if (legacyPropertyExtensions.length > 0) {
                        Map<String, Object> materialized = authScheme.materialize();
                        for (var extension : legacyPropertyExtensions) {
                            extension.extractEndpointProperties(builder, context, materialized, headers);
                        }
                    }
                } else {
                    for (var extension : extensions) {
                        extension.extractEndpointProperties(builder, context, properties, headers);
                    }
                }
                return builder.build();
            } catch (RuntimeException e) {
                endpointBuilder = Endpoint.builder();
                throw e;
            }
        }
    }

    private static final class GeneratedAuthSchemeProperties
            extends java.util.AbstractMap<String, Object>
            implements PropertyGetter {
        private Object v0;
        private String k0;
        private Object v1;
        private String k1;
        private Object v2;
        private String k2;
        private Object v3;
        private String k3;
        private Object extraValue;
        private String extraKey;

        void set(
                Object extraValue,
                String extraKey,
                Object v0,
                String k0,
                Object v1,
                String k1,
                Object v2,
                String k2,
                Object v3,
                String k3
        ) {
            this.extraValue = extraValue;
            this.extraKey = extraKey;
            this.v0 = v0;
            this.k0 = k0;
            this.v1 = v1;
            this.k1 = k1;
            this.v2 = v2;
            this.k2 = k2;
            this.v3 = v3;
            this.k3 = k3;
        }

        @Override
        public Object getProperty(String name) {
            if (name.equals(k0)) {
                return v0;
            } else if (name.equals(k1)) {
                return v1;
            } else if (name.equals(k2)) {
                return v2;
            } else if (name.equals(k3)) {
                return v3;
            }
            return null;
        }

        @Override
        public Object get(Object key) {
            if ("authSchemes".equals(key)) {
                return List.of(this);
            }
            return extraKey != null && extraKey.equals(key) ? extraValue : null;
        }

        @Override
        public java.util.Set<Entry<String, Object>> entrySet() {
            return materialize().entrySet();
        }

        Map<String, Object> materialize() {
            Map<String, Object> entry = new java.util.HashMap<>(5);
            for (String name : List.of(
                    "name",
                    "signingName",
                    "signingRegion",
                    "disableDoubleEncoding",
                    "signingRegionSet")) {
                Object value = getProperty(name);
                if (value != null) {
                    entry.put(name, value);
                }
            }
            if (extraKey == null) {
                return Map.of("authSchemes", List.of(entry));
            }
            Map<String, Object> result = new java.util.HashMap<>(2);
            result.put(extraKey, extraValue);
            result.put("authSchemes", List.of(entry));
            return java.util.Collections.unmodifiableMap(result);
        }
    }
}
