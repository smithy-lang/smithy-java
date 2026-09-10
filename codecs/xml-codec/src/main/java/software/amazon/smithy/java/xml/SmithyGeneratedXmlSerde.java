/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;

/**
 * Entry point from {@link XmlCodec} into generated XML codecs.
 *
 * <p>Every method here returns a sentinel ({@code null} or {@code false}) when no generated codec is
 * available, which is the contract that lets generation fail silently and the dispatch serde take over.
 *
 * <p>There is one instance for the process, and its registries are keyed by settings <em>identity</em>.
 * Both halves of that are load bearing. {@code AwsQueryClientProtocol} constructs a fresh
 * {@code XmlCodec} for every error response it parses, so a serde owned by the codec would emit and
 * load a new hidden class per response no matter how well settings were canonicalized; interning the
 * settings then makes every one of those codecs share a registry and a generated class. Nothing is
 * retained for longer than the shapes involved: {@link RuntimeCodecRegistry} hangs its entries off a
 * {@code ClassValue} on the shape class.
 *
 * <p>The {@code MAX_SETTINGS} bound only matters for a caller that defeats interning; it keeps the
 * number of live registries, and therefore hidden classes, finite.
 */
final class SmithyGeneratedXmlSerde {
    private static final int MAX_SETTINGS = 16;
    private static final int MAX_SUBSETS = 16;

    static final SmithyGeneratedXmlSerde INSTANCE = new SmithyGeneratedXmlSerde();

    /**
     * Adapters from a caller's {@link MemberSubsetCodec.MemberSubset} to the registry's selector.
     *
     * <p>The registry compares selectors by identity, so an adapter has to live as long as the subset
     * it wraps: a fresh one per call would miss the codec cache and generate a class per body written.
     * The bound is here because this map is keyed on caller-supplied instances, and a caller that
     * ignores the singleton contract should serialize slowly rather than grow a cache without limit.
     */
    private static final ConcurrentHashMap<
            MemberSubsetCodec.MemberSubset,
            RuntimeCodecBackend.MemberSelector> SELECTORS = new ConcurrentHashMap<>();

    private final IdentityHashMap<XmlSettings, RuntimeCodecRegistry<GeneratedXmlCodec>> registries =
            new IdentityHashMap<>();
    private final ArrayDeque<XmlSettings> settingsOrder = new ArrayDeque<>();

    /**
     * Private to this instance rather than borrowed from a codec: the generator only reads list and
     * map framing out of it, which is a pure function of the schema, so sharing one cache across
     * codecs is both correct and cheaper than a cache per codec.
     */
    private final XmlInfo xmlInfo = new XmlInfo();

    private volatile CachedRegistry lastRegistry;
    private volatile LastEntry lastEntry;

    private SmithyGeneratedXmlSerde() {}

    /**
     * @param schema the schema the caller passed to {@code writeStruct}, which is the member schema when
     *               the structure is an {@code @httpPayload} and therefore names the root element.
     */
    ByteBuffer serialize(SerializableStruct value, Schema schema, XmlSettings settings) {
        return serialize(value, schema, settings, null);
    }

    /**
     * Serializes {@code value}, writing only the members {@code subset} includes.
     *
     * @param subset members to write, or null to write the whole shape.
     * @return the bytes, or null if no codec could be generated for this shape and subset.
     */
    ByteBuffer serialize(
            SerializableStruct value,
            Schema schema,
            XmlSettings settings,
            MemberSubsetCodec.MemberSubset subset
    ) {
        if (!generatable(value, schema)) {
            return null;
        }
        RuntimeCodecBackend.MemberSelector selector = selectorFor(subset);
        if (selector == null) {
            return null;
        }
        Entry entry = entry(schema, settings, subset, selector);
        if (entry == null) {
            return null;
        }
        XmlCodegenWriter writer = XmlCodegenWriter.acquire(settings);
        try {
            entry.codec.write(value, writer, entry.open, entry.close);
            return writer.detach();
        } finally {
            XmlCodegenWriter.release(writer);
        }
    }

    boolean serializeTo(SerializableStruct value, Schema schema, OutputStream sink, XmlSettings settings) {
        if (!generatable(value, schema)) {
            return false;
        }
        Entry entry = entry(schema, settings, null, RuntimeCodecBackend.MemberSelector.all());
        if (entry == null) {
            return false;
        }
        XmlCodegenWriter writer = XmlCodegenWriter.acquire(settings);
        try {
            writer.sink(sink);
            entry.codec.write(value, writer, entry.open, entry.close);
            writer.flush();
            return true;
        } finally {
            XmlCodegenWriter.release(writer);
        }
    }

    /**
     * @param schema the schema the caller passed to {@code readStruct}, which may be a member schema.
     * @return true when a generated reader consumed the element, false when the caller must fall back.
     */
    boolean read(
            SmithyXmlDeserializer reader,
            Schema schema,
            ShapeBuilder<?> builder,
            XmlSettings settings
    ) {
        return read(reader, schema, builder, settings, null);
    }

    /**
     * Reads only the root members selected by {@code subset}; nested structures remain whole.
     */
    boolean read(
            SmithyXmlDeserializer reader,
            Schema schema,
            ShapeBuilder<?> builder,
            XmlSettings settings,
            MemberSubsetCodec.MemberSubset subset
    ) {
        // Only the codec is needed here: unlike writing, reading never names the root element, so the
        // XmlRootTags half of the entry cache would be dead weight.
        RuntimeCodecBackend.MemberSelector selector = selectorFor(subset);
        if (selector == null) {
            return false;
        }
        Schema root = schema.isMember() ? schema.memberTarget() : schema;
        GeneratedXmlCodec codec = registry(settings).get(root, settings, selector);
        return codec != null && codec.read(reader, builder);
    }

    synchronized void clear() {
        for (RuntimeCodecRegistry<GeneratedXmlCodec> registry : registries.values()) {
            registry.clear();
        }
        registries.clear();
        settingsOrder.clear();
        lastRegistry = null;
        lastEntry = null;
    }

    /**
     * Whether a codec generated for {@code schema} can be handed {@code value}.
     *
     * <p>A generated writer reads a shape through its concrete class, so it can only accept an instance
     * of that class. Callers legitimately pass structures that report a shape's schema while being some
     * other class: {@code StructBodyProxy} hides the members an HTTP binding owns, and an empty-struct
     * stand-in reports a shape it does not extend. Those have to take the dispatch path instead of
     * failing a cast inside generated code.
     */
    private static boolean generatable(SerializableStruct value, Schema schema) {
        Class<?> shapeClass = (schema.isMember() ? schema.memberTarget() : schema).shapeClass();
        return shapeClass != null && shapeClass.isInstance(value);
    }

    /** @return the registry selector for {@code subset}, or null when too many subsets are live. */
    private static RuntimeCodecBackend.MemberSelector selectorFor(MemberSubsetCodec.MemberSubset subset) {
        if (subset == null) {
            return RuntimeCodecBackend.MemberSelector.all();
        }
        RuntimeCodecBackend.MemberSelector selector = SELECTORS.get(subset);
        if (selector != null) {
            return selector;
        }
        return SELECTORS.size() < MAX_SUBSETS
                ? SELECTORS.computeIfAbsent(subset, s -> s::includes)
                : null;
    }

    /**
     * Resolves the generated codec for {@code schema} together with the root tags to hand it.
     *
     * <p>The cache is keyed on the caller's schema rather than the shape it targets, because two members
     * of the same structure can name the root element differently. The codec itself is shared between
     * them: it is generated per shape class and knows nothing about its own element name.
     */
    private Entry entry(
            Schema schema,
            XmlSettings settings,
            MemberSubsetCodec.MemberSubset subset,
            RuntimeCodecBackend.MemberSelector selector
    ) {
        LastEntry cached = lastEntry;
        if (cached != null && cached.schema.get() == schema
                && cached.settings == settings
                && cached.subset == subset) {
            GeneratedXmlCodec codec = cached.codec.get();
            if (codec != null) {
                return new Entry(codec, cached.open, cached.close);
            }
        }
        Schema root = schema.isMember() ? schema.memberTarget() : schema;
        GeneratedXmlCodec codec = registry(settings).get(root, settings, selector);
        if (codec == null) {
            return null;
        }
        XmlRootTags tags = XmlRootTags.of(schema, settings);
        lastEntry = new LastEntry(
                new WeakReference<>(schema),
                settings,
                subset,
                new WeakReference<>(codec),
                tags.open(),
                tags.close());
        return new Entry(codec, tags.open(), tags.close());
    }

    private RuntimeCodecRegistry<GeneratedXmlCodec> registry(XmlSettings settings) {
        CachedRegistry cached = lastRegistry;
        if (cached != null && cached.settings == settings) {
            return cached.registry;
        }
        synchronized (this) {
            RuntimeCodecRegistry<GeneratedXmlCodec> result = registries.get(settings);
            if (result == null) {
                result = new RuntimeCodecRegistry<>(new XmlRuntimeCodegenBackend(xmlInfo));
                registries.put(settings, result);
                settingsOrder.addLast(settings);
                if (registries.size() > MAX_SETTINGS) {
                    XmlSettings evicted = settingsOrder.removeFirst();
                    RuntimeCodecRegistry<GeneratedXmlCodec> evictedRegistry = registries.remove(evicted);
                    if (evictedRegistry != null) {
                        evictedRegistry.clear();
                    }
                }
            }
            lastRegistry = new CachedRegistry(settings, result);
            return result;
        }
    }

    private record CachedRegistry(
            XmlSettings settings,
            RuntimeCodecRegistry<GeneratedXmlCodec> registry) {}

    /** A resolved codec and its root tags. Never escapes the call that produced it. */
    private record Entry(GeneratedXmlCodec codec, byte[] open, byte[] close) {}

    /**
     * The single-entry resolution cache. The codec is held weakly so this cache never keeps a hidden
     * class, or the shape class it was generated for, alive on its own.
     */
    private record LastEntry(
            WeakReference<Schema> schema,
            XmlSettings settings,
            MemberSubsetCodec.MemberSubset subset,
            WeakReference<GeneratedXmlCodec> codec,
            byte[] open,
            byte[] close) {}
}
