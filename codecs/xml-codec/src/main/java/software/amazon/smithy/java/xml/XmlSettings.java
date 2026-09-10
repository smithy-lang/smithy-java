/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * The subset of {@link XmlCodec} configuration that generated codecs depend on, canonicalized so
 * equal configurations share one instance.
 *
 * <p>Interning is not a micro-optimization here. Generated codecs are cached against the settings by
 * <em>identity</em>, and callers create codecs freely: {@code AwsQueryClientProtocol} builds a fresh
 * {@code XmlCodec} for every error response it parses. Without canonicalization each of those codecs
 * would miss the cache and emit, load, and retain a new hidden class per response.
 *
 * <p>Only fields that change generated output belong here. {@code wrapperElements} and
 * {@code strictRootElement} change how a document is entered on the read side; {@code defaultNamespace}
 * is baked into the root element's opening tag on the write side.
 */
final class XmlSettings {

    /**
     * Bounds the intern table. Settings come from application code rather than from the wire, so a
     * realistic process holds a handful; the cap only exists so a pathological caller degrades to
     * uncached generation instead of leaking.
     */
    private static final int MAX_INTERNED = 64;

    private static final ConcurrentHashMap<XmlSettings, XmlSettings> INTERNED = new ConcurrentHashMap<>();

    private final List<String> wrapperElements;
    private final XmlNamespaceTrait defaultNamespace;
    private final boolean strictRootElement;
    private final int hashCode;

    private XmlSettings(
            List<String> wrapperElements,
            XmlNamespaceTrait defaultNamespace,
            boolean strictRootElement
    ) {
        this.wrapperElements = wrapperElements;
        this.defaultNamespace = defaultNamespace;
        this.strictRootElement = strictRootElement;
        this.hashCode = Objects.hash(wrapperElements, defaultNamespace, strictRootElement);
    }

    static XmlSettings of(
            List<String> wrapperElements,
            XmlNamespaceTrait defaultNamespace,
            boolean strictRootElement
    ) {
        XmlSettings candidate = new XmlSettings(
                List.copyOf(wrapperElements),
                defaultNamespace,
                strictRootElement);
        XmlSettings existing = INTERNED.get(candidate);
        if (existing != null) {
            return existing;
        }
        if (INTERNED.size() >= MAX_INTERNED) {
            // Uncached, so generated codecs keyed on it are regenerated. Correct, merely slow.
            return candidate;
        }
        existing = INTERNED.putIfAbsent(candidate, candidate);
        return existing != null ? existing : candidate;
    }

    List<String> wrapperElements() {
        return wrapperElements;
    }

    XmlNamespaceTrait defaultNamespace() {
        return defaultNamespace;
    }

    boolean strictRootElement() {
        return strictRootElement;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof XmlSettings that
                && strictRootElement == that.strictRootElement
                && wrapperElements.equals(that.wrapperElements)
                && Objects.equals(defaultNamespace, that.defaultNamespace);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
