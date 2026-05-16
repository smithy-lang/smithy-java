/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Pre-computes HTTP binding data on Schema objects at construction time so the per-request hot path can avoid
 * trait lookups, type checks, and dispatch on a generic switch.
 *
 * <p>All internals are package-private and can be changed freely.
 */
@SmithyInternalApi
public final class HttpBindingSchemaExtensions
        implements SchemaExtensionProvider<HttpBindingSchemaExtensions.HttpBindingExt> {

    /**
     * Extension key for HTTP binding pre-computed data.
     */
    public static final SchemaExtensionKey<HttpBindingExt> KEY = new SchemaExtensionKey<>();

    /**
     * Look up the {@link MemberBinding} for a member schema. Throws if no binding or not a member.
     */
    static MemberBinding memberBindingOf(Schema schema) {
        var ext = schema.getExtension(KEY);
        if (ext == null) {
            throw new IllegalStateException(
                    "Schema " + schema.id() + " has no HTTP binding extension");
        }
        if (!(ext instanceof MemberBinding mb)) {
            throw new IllegalStateException(
                    "Schema " + schema.id() + " has an HTTP binding extension that is not a member binding");
        }
        return mb;
    }

    /**
     * Look up the {@link OperationBinding} for an operation schema. Throws if no binding or not an operation.
     */
    static OperationBinding operationBindingOf(Schema schema) {
        var ext = schema.getExtension(KEY);
        if (ext == null) {
            throw new IllegalStateException(
                    "Schema " + schema.id() + " has no HTTP binding extension");
        }
        if (!(ext instanceof OperationBinding ob)) {
            throw new IllegalStateException(
                    "Schema " + schema.id() + " has an HTTP binding extension that is not an operation binding");
        }
        return ob;
    }

    /**
     * Look up the {@link StructBindings} for a structure schema. Throws if no binding or not a structure.
     */
    static StructBindings structBindingsOf(Schema schema) {
        var ext = schema.getExtension(KEY);
        if (ext == null) {
            throw new IllegalStateException("Schema " + schema.id() + " has no HTTP binding extension");
        }
        if (!(ext instanceof StructBindings sb)) {
            throw new IllegalStateException(
                    "Schema " + schema.id() + " has an HTTP binding extension that is not a structure binding");
        }
        return sb;
    }

    private static final Schema[] NO_SCHEMAS = new Schema[0];
    private static final String[] NO_QUERY_LITERALS = new String[0];

    /**
     * Binding kind for a single member, derived from the traits applied to it.
     *
     * <p>This is the protocol-test-friendly logical kind. The serializer / deserializer use direction-specific arrays
     * on {@link StructBindings} which collapse {@link #STATUS} to {@link #BODY} for request direction and
     * {@link #LABEL} / {@link #QUERY} / {@link #QUERY_PARAMS} to {@link #BODY} for response direction.
     */
    enum Binding {
        HEADER,
        QUERY,
        PAYLOAD,
        BODY,
        LABEL,
        STATUS,
        PREFIX_HEADERS,
        QUERY_PARAMS
    }

    /**
     * Sealed marker for HTTP binding extension data attached to a schema.
     */
    public sealed interface HttpBindingExt permits MemberBinding, StructBindings, OperationBinding {}

    /**
     * Pre-computed binding data for a single member schema.
     *
     * @param kind  the member's binding kind, resolved once from the member's traits.
     * @param wireName  the resolved wire name: the header field name for {@code @httpHeader}, the query parameter
     *      name for {@code @httpQuery}, the prefix string for {@code @httpPrefixHeaders}, or {@code null}.
     * @param headerName the canonicalized header name for {@code @httpHeader}, or {@code null} for non-header bindings
     *      and dynamically-generated header names.
     * @param isList  whether the member's resolved target type is {@link ShapeType#LIST}.
     * @param hasMediaType  whether the member carries an {@code @mediaType} trait.
     * @param timestampFormatter  the effective timestamp formatter for this binding location, with any explicit
     *      {@code @timestampFormat} trait already resolved. Defaults to {@code HTTP_DATE} for
     *      header bindings, {@code DATE_TIME} for query / label bindings, and {@code null} otherwise.
     */
    record MemberBinding(
            Binding kind,
            String wireName,
            HeaderName headerName,
            boolean isList,
            boolean hasMediaType,
            TimestampFormatter timestampFormatter) implements HttpBindingExt {}

    /**
     * HTTP binding extension data for a structure or union schema for request and response bindings.
     *
     * <p>The volatile lazy fields use a benign-race CAS-free idiom.
     */
    static final class StructBindings implements HttpBindingExt {
        private final Schema schema;
        private volatile RequestBinding request;
        private volatile ResponseBinding response;

        StructBindings(Schema schema) {
            this.schema = schema;
        }

        RequestBinding request() {
            var r = request;
            if (r == null) {
                r = buildRequestBinding(schema);
                request = r;
            }
            return r;
        }

        ResponseBinding response() {
            var r = response;
            if (r == null) {
                r = buildResponseBinding(schema);
                response = r;
            }
            return r;
        }
    }

    /**
     * Pre-computed request-direction binding data for one structure / union schema. Built lazily by
     * {@link StructBindings#request()} on first request-side access.
     */
    static final class RequestBinding {
        // Per-member request-direction Binding kind, indexed by member.memberIndex().
        // Used by the binding-serializer's hot-path switch.
        final Binding[] bindings;
        // Direction-neutral MemberBinding metadata indexed by member.memberIndex().
        // Saves the per-write Schema.getExtension(KEY) lookup chain.
        final MemberBinding[] memberBindings;
        // @httpHeader members split by target shape.
        final Map<String, Schema> scalarHeadersByName;
        final Schema[] listHeaderMembers;
        final HeaderName[] listHeaderNames;
        final Schema[] queryMembers;
        final String[] queryWireNames;
        final Schema[] labelMembers;
        final Schema[] queryParamsMembers;
        final Schema[] prefixHeadersMembers;
        final Schema payloadMember;
        final boolean hasBody;
        final boolean hasPayload;
        // Set of all declared header wire names. Used by HttpPrefixHeadersSerializer to skip
        // map keys that already collide with explicit @httpHeader members.
        final Set<String> headerWireNames;
        // Whether {@code Content-Type} appears as an @httpHeader member of this struct (input-only concept).
        final boolean inputContentTypeHeader;
        // Estimated initial capacity (in name-value pairs) for the ArrayHttpHeaders we allocate per request.
        final int headerCount;
        // Lazy PathSerializer — built on first request through {@link #pathSerializer}.
        private volatile PathSerializer pathSerializer;

        RequestBinding(
                Binding[] bindings,
                MemberBinding[] memberBindings,
                Map<String, Schema> scalarHeadersByName,
                Schema[] listHeaderMembers,
                HeaderName[] listHeaderNames,
                Schema[] queryMembers,
                String[] queryWireNames,
                Schema[] labelMembers,
                Schema[] queryParamsMembers,
                Schema[] prefixHeadersMembers,
                Schema payloadMember,
                boolean hasBody,
                boolean hasPayload,
                Set<String> headerWireNames,
                boolean inputContentTypeHeader,
                int headerCount
        ) {
            this.bindings = bindings;
            this.memberBindings = memberBindings;
            this.scalarHeadersByName = scalarHeadersByName;
            this.listHeaderMembers = listHeaderMembers;
            this.listHeaderNames = listHeaderNames;
            this.queryMembers = queryMembers;
            this.queryWireNames = queryWireNames;
            this.labelMembers = labelMembers;
            this.queryParamsMembers = queryParamsMembers;
            this.prefixHeadersMembers = prefixHeadersMembers;
            this.payloadMember = payloadMember;
            this.hasBody = hasBody;
            this.hasPayload = hasPayload;
            this.headerWireNames = headerWireNames;
            this.inputContentTypeHeader = inputContentTypeHeader;
            this.headerCount = headerCount;
        }

        PathSerializer pathSerializer(HttpTrait httpTrait, Schema inputSchema) {
            var p = pathSerializer;
            if (p != null) {
                return p;
            }
            p = new PathSerializer(httpTrait, inputSchema);
            pathSerializer = p;
            return p;
        }

        boolean writeBody(boolean omitEmptyPayload) {
            return hasBody || (!omitEmptyPayload && !hasPayload);
        }
    }

    /**
     * Pre-computed response-direction binding data for one structure / union schema. Built lazily
     * by {@link StructBindings#response()} on first response-side access.
     */
    record ResponseBinding(
            Binding[] bindings,
            MemberBinding[] memberBindings,
            Map<String, Schema> scalarHeadersByName,
            Schema[] listHeaderMembers,
            HeaderName[] listHeaderNames,
            Schema[] prefixHeadersMembers,
            Schema payloadMember,
            Schema statusMember,
            boolean hasBody,
            boolean hasPayload,
            Set<String> headerWireNames,
            int headerCount,
            int defaultStatus) {
        boolean writeBody(boolean omitEmptyPayload) {
            return hasBody || (!omitEmptyPayload && !hasPayload);
        }
    }

    /**
     * Pre-computed HTTP-binding data for an operation schema.
     *
     * @param httpTrait the cached {@code @http} trait — saves an {@code expectTrait} call per request.
     * @param queryLiterals flat array of (name, value) pairs from the URI's static query literals, or empty.
     * @param defaultResponseStatus default response status declared by the {@code @http} trait.
     */
    record OperationBinding(
            HttpTrait httpTrait,
            String[] queryLiterals,
            int defaultResponseStatus) implements HttpBindingExt {}

    @Override
    public SchemaExtensionKey<HttpBindingExt> key() {
        return KEY;
    }

    @Override
    public HttpBindingExt provide(Schema schema) {
        if (schema.isMember()) {
            return forMember(schema);
        }
        var type = schema.type();
        if (type == ShapeType.STRUCTURE || type == ShapeType.UNION) {
            return forStruct(schema);
        }
        if (type == ShapeType.OPERATION && schema.hasTrait(TraitKey.HTTP_TRAIT)) {
            return forOperation(schema);
        }
        return null;
    }

    private static MemberBinding forMember(Schema m) {
        // Header binding: needs wire field name and list-ness.
        var hdr = m.getTrait(TraitKey.HTTP_HEADER_TRAIT);
        if (hdr != null) {
            // Header wire names are canonicalized (lowercased) at schema-build time. HTTP header names are
            // case-insensitive, so storing them canonical means runtime lookup paths can compare them by
            // identity / hash without re-canonicalizing per call.
            String canonicalName = HeaderName.canonicalize(hdr.getValue());
            return new MemberBinding(
                    Binding.HEADER,
                    canonicalName,
                    HeaderName.of(canonicalName),
                    isListMember(m),
                    m.hasTrait(TraitKey.MEDIA_TYPE_TRAIT),
                    timestampFormatter(m, TimestampFormatter.Prelude.HTTP_DATE));
        }

        // Query string parameter.
        var q = m.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (q != null) {
            return new MemberBinding(
                    Binding.QUERY,
                    q.getValue(),
                    null,
                    isListMember(m),
                    false,
                    timestampFormatter(m, TimestampFormatter.Prelude.DATE_TIME));
        }

        // @httpPrefixHeaders has a prefix string as its value. Canonicalized for the same reason as @httpHeader:
        // header name matching is case-insensitive. Empty prefix is permitted by the trait (it means "all headers go
        // in this map"); skip canonicalization for it since HeaderName.canonicalize rejects empty input.
        var pfx = m.getTrait(TraitKey.HTTP_PREFIX_HEADERS_TRAIT);
        if (pfx != null) {
            String pfxValue = pfx.getValue();
            String canonicalPrefix = pfxValue.isEmpty() ? pfxValue : HeaderName.canonicalize(pfxValue);
            return new MemberBinding(
                    Binding.PREFIX_HEADERS,
                    canonicalPrefix,
                    null,
                    false,
                    false,
                    null);
        }

        if (m.hasTrait(TraitKey.HTTP_LABEL_TRAIT)) {
            return new MemberBinding(
                    Binding.LABEL,
                    m.memberName(),
                    null,
                    false,
                    false,
                    timestampFormatter(m, TimestampFormatter.Prelude.DATE_TIME));
        }

        if (m.hasTrait(TraitKey.HTTP_QUERY_PARAMS_TRAIT)) {
            return new MemberBinding(
                    Binding.QUERY_PARAMS,
                    null,
                    null,
                    false,
                    false,
                    null);
        }

        if (m.hasTrait(TraitKey.HTTP_PAYLOAD_TRAIT)) {
            return new MemberBinding(
                    Binding.PAYLOAD,
                    null,
                    null,
                    false,
                    m.hasTrait(TraitKey.MEDIA_TYPE_TRAIT),
                    null);
        }

        if (m.hasTrait(TraitKey.HTTP_RESPONSE_CODE_TRAIT)) {
            return new MemberBinding(
                    Binding.STATUS,
                    null,
                    null,
                    false,
                    false,
                    null);
        }

        return new MemberBinding(Binding.BODY, null, null, false, false, null);
    }

    private static boolean isListMember(Schema m) {
        // Resolve through member -> target if we got a member schema.
        var target = m.isMember() ? m.memberTarget() : m;
        return target.type() == ShapeType.LIST;
    }

    private static TimestampFormatter timestampFormatter(Schema member, TimestampFormatter defaultFormatter) {
        var trait = member.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        return trait == null ? defaultFormatter : TimestampFormatter.of(trait);
    }

    private static StructBindings forStruct(Schema struct) {
        return new StructBindings(struct);
    }

    /**
     * Build the request-direction binding for one struct/union schema. Walks members once, classifies each by trait
     * kind under the request-direction view, and builds the parallel arrays needed by the binding serializer.
     */
    static RequestBinding buildRequestBinding(Schema struct) {
        List<Schema> headers = new ArrayList<>();
        List<Schema> queries = new ArrayList<>();
        List<Schema> labels = new ArrayList<>();
        List<Schema> queryParams = new ArrayList<>();
        List<Schema> prefixHeaders = new ArrayList<>();
        Schema payload = null;
        boolean hasBody = false;
        boolean hasPayload = false;

        int memberCount = struct.members().size();
        Binding[] bindings = new Binding[memberCount];
        MemberBinding[] memberBindings = new MemberBinding[memberCount];

        for (Schema m : struct.members()) {
            var ext = (MemberBinding) m.getExtension(KEY);
            int idx = m.memberIndex();
            memberBindings[idx] = ext;
            switch (ext.kind()) {
                case HEADER -> {
                    headers.add(m);
                    bindings[idx] = Binding.HEADER;
                }
                case PREFIX_HEADERS -> {
                    prefixHeaders.add(m);
                    bindings[idx] = Binding.PREFIX_HEADERS;
                }
                case PAYLOAD -> {
                    payload = m;
                    hasPayload = true;
                    bindings[idx] = Binding.PAYLOAD;
                }
                case QUERY -> {
                    queries.add(m);
                    bindings[idx] = Binding.QUERY;
                }
                case QUERY_PARAMS -> {
                    queryParams.add(m);
                    bindings[idx] = Binding.QUERY_PARAMS;
                }
                case LABEL -> {
                    labels.add(m);
                    bindings[idx] = Binding.LABEL;
                }
                case STATUS -> {
                    // STATUS has no meaning request-side — fold into BODY.
                    hasBody = true;
                    bindings[idx] = Binding.BODY;
                }
                case BODY -> {
                    hasBody = true;
                    bindings[idx] = Binding.BODY;
                }
            }
        }

        Set<String> headerWireNames;
        boolean hasContentTypeHeader = false;
        if (headers.isEmpty() && prefixHeaders.isEmpty()) {
            headerWireNames = Set.of();
        } else {
            HashSet<String> set = HashSet.newHashSet(headers.size());
            for (Schema m : headers) {
                String wireName = ((MemberBinding) m.getExtension(KEY)).wireName();
                set.add(wireName);
                // wireName is already canonical (lowercase) here, so use
                // exact equality rather than a case-insensitive compare.
                if (!hasContentTypeHeader && wireName.equals("content-type")) {
                    hasContentTypeHeader = true;
                }
            }
            headerWireNames = Set.copyOf(set);
        }

        Schema[] listHeaderMembers = listHeaderMembers(headers);
        HeaderName[] listHeaderNames = listHeaderNames(listHeaderMembers);
        Schema[] queryArr = toArray(queries);
        String[] queryWireNames = queryWireNames(queryArr);

        // +4 covers auto-set headers (Content-Type, Content-Length, …); cap at 32 to avoid
        // over-allocating for AWS-S3-style structs that declare 50+ headers but populate few.
        int headerCount = Math.min(headers.size() + prefixHeaders.size() + 4, 32);

        return new RequestBinding(
                bindings,
                memberBindings,
                scalarHeadersByName(headers),
                listHeaderMembers,
                listHeaderNames,
                queryArr,
                queryWireNames,
                toArray(labels),
                toArray(queryParams),
                toArray(prefixHeaders),
                payload,
                hasBody,
                hasPayload,
                headerWireNames,
                hasContentTypeHeader,
                headerCount);
    }

    /**
     * Build the response-direction binding for one struct/union schema. Walks members once under the
     * response-direction view.
     */
    static ResponseBinding buildResponseBinding(Schema struct) {
        List<Schema> headers = new ArrayList<>();
        List<Schema> prefixHeaders = new ArrayList<>();
        Schema payload = null;
        Schema statusMember = null;
        boolean hasBody = false;
        boolean hasPayload = false;

        int memberCount = struct.members().size();
        Binding[] bindings = new Binding[memberCount];
        MemberBinding[] memberBindings = new MemberBinding[memberCount];

        for (Schema m : struct.members()) {
            var ext = (MemberBinding) m.getExtension(KEY);
            int idx = m.memberIndex();
            memberBindings[idx] = ext;
            switch (ext.kind()) {
                case HEADER -> {
                    headers.add(m);
                    bindings[idx] = Binding.HEADER;
                }
                case PREFIX_HEADERS -> {
                    prefixHeaders.add(m);
                    bindings[idx] = Binding.PREFIX_HEADERS;
                }
                case PAYLOAD -> {
                    payload = m;
                    hasPayload = true;
                    bindings[idx] = Binding.PAYLOAD;
                }
                case STATUS -> {
                    statusMember = m;
                    bindings[idx] = Binding.STATUS;
                }
                case QUERY, QUERY_PARAMS, LABEL, BODY -> {
                    // Request-only kinds are folded into BODY in response direction.
                    hasBody = true;
                    bindings[idx] = Binding.BODY;
                }
            }
        }

        Set<String> headerWireNames;
        if (headers.isEmpty() && prefixHeaders.isEmpty()) {
            headerWireNames = Set.of();
        } else {
            HashSet<String> set = HashSet.newHashSet(headers.size());
            for (Schema m : headers) {
                set.add(((MemberBinding) m.getExtension(KEY)).wireName());
            }
            headerWireNames = Set.copyOf(set);
        }

        Schema[] listHeaderMembers = listHeaderMembers(headers);
        HeaderName[] listHeaderNames = listHeaderNames(listHeaderMembers);

        int headerCount = Math.min(headers.size() + prefixHeaders.size() + 4, 32);

        // Default response status from @httpError or @error trait, or -1 for non-error responses.
        int defaultStatus;
        if (struct.hasTrait(TraitKey.HTTP_ERROR_TRAIT)) {
            defaultStatus = struct.expectTrait(TraitKey.HTTP_ERROR_TRAIT).getCode();
        } else if (struct.hasTrait(TraitKey.ERROR_TRAIT)) {
            defaultStatus = struct.expectTrait(TraitKey.ERROR_TRAIT).getDefaultHttpStatusCode();
        } else {
            defaultStatus = -1;
        }

        return new ResponseBinding(
                bindings,
                memberBindings,
                scalarHeadersByName(headers),
                listHeaderMembers,
                listHeaderNames,
                toArray(prefixHeaders),
                payload,
                statusMember,
                hasBody,
                hasPayload,
                headerWireNames,
                headerCount,
                defaultStatus);
    }

    /**
     * Build a {@code Map<lowercase-wire-name, Schema>} of header members whose target shape is NOT a list.
     * Used by the response-driven deserializer loop: iterate the response's actual headers and dispatch
     * via the map rather than scanning all declared header members.
     *
     * <p>Keys are lowercased once at build time so per-call lookups don't have to canonicalize. ArrayHttpHeaders
     * stores names in canonical (lowercase) form already, so {@code map.get(name)} during iteration needs no
     * further normalization.
     */
    private static Map<String, Schema> scalarHeadersByName(List<Schema> members) {
        Map<String, Schema> result = null;
        for (Schema m : members) {
            if (isListMember(m)) {
                continue;
            }
            var memberExt = (MemberBinding) m.getExtension(KEY);
            if (result == null) {
                result = new HashMap<>(members.size());
            }
            result.put(memberExt.wireName(), m);
        }
        return result == null ? Map.of() : Map.copyOf(result);
    }

    /**
     * Header members whose target shape IS a list. List headers need
     * {@code allValues(name)} rather than {@code firstValue(name)}, so they
     * stay on the schema-driven loop. Almost always empty for AWS shapes.
     */
    private static Schema[] listHeaderMembers(List<Schema> members) {
        List<Schema> list = null;
        for (Schema m : members) {
            if (!isListMember(m)) {
                continue;
            }
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(m);
        }
        return list == null ? NO_SCHEMAS : list.toArray(new Schema[0]);
    }

    private static Schema[] toArray(List<Schema> list) {
        return list.isEmpty() ? NO_SCHEMAS : list.toArray(new Schema[0]);
    }

    private static final HeaderName[] NO_HEADER_NAMES = new HeaderName[0];
    private static final String[] NO_STRINGS = new String[0];

    /**
     * Pre-resolve canonical {@link HeaderName}s parallel to a {@code Schema[]} of
     * list-header members. Reading the name later in the deserializer becomes a
     * direct array load instead of a {@code memberBindingOf(member).headerName()}
     * lookup chain.
     */
    private static HeaderName[] listHeaderNames(Schema[] members) {
        if (members.length == 0) {
            return NO_HEADER_NAMES;
        }
        HeaderName[] names = new HeaderName[members.length];
        for (int i = 0; i < members.length; i++) {
            names[i] = ((MemberBinding) members[i].getExtension(KEY)).headerName();
        }
        return names;
    }

    /**
     * Pre-resolve query wire names parallel to a {@code Schema[]} of query
     * members. Saves a per-member {@code memberBindingOf} call in the
     * request-deserializer's query loop.
     */
    private static String[] queryWireNames(Schema[] members) {
        if (members.length == 0) {
            return NO_STRINGS;
        }
        String[] names = new String[members.length];
        for (int i = 0; i < members.length; i++) {
            names[i] = ((MemberBinding) members[i].getExtension(KEY)).wireName();
        }
        return names;
    }

    private static OperationBinding forOperation(Schema schema) {
        var httpTrait = schema.expectTrait(TraitKey.HTTP_TRAIT);
        var uriPattern = httpTrait.getUri();

        // Flatten query literals into a (name, value) pair array. The trait's own map iteration is stable for a
        // given trait instance, but this avoids the per-call iterator + Map.Entry allocations.
        var queryLiteralMap = uriPattern.getQueryLiterals();
        String[] queryLiterals;
        if (queryLiteralMap.isEmpty()) {
            queryLiterals = NO_QUERY_LITERALS;
        } else {
            queryLiterals = new String[2 * queryLiteralMap.size()];
            int i = 0;
            for (var entry : queryLiteralMap.entrySet()) {
                queryLiterals[i++] = entry.getKey();
                queryLiterals[i++] = entry.getValue();
            }
        }

        return new OperationBinding(httpTrait, queryLiterals, httpTrait.getCode());
    }
}
