/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecPlan;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.RuntimeCodegenMode;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

public class HttpBindingWriterTest {

    private static final Codec CODEC = JsonCodec.builder().build();
    private static final SmithyUri ENDPOINT = SmithyUri.of("https://example.com");
    private static final Instant WHEN = Instant.parse("2026-08-16T01:02:03Z");

    @BeforeAll
    public static void requireCodegen() {
        assumeTrue(
                RuntimeCodegenFeature.available(),
                "Runtime code generation is not available in this JVM");
    }

    @Test
    public void writesEveryBoundTypeAsAHeader() {
        var headers = generated(fullyPopulated()).headers();

        assertThat(headers.firstValue("x-text"), equalTo("plain"));
        assertThat(headers.firstValue("x-flag"), equalTo("true"));
        assertThat(headers.firstValue("x-byte"), equalTo("7"));
        assertThat(headers.firstValue("x-short"), equalTo("-9"));
        assertThat(headers.firstValue("x-int"), equalTo("11"));
        assertThat(headers.firstValue("x-long"), equalTo("12345678901"));
        assertThat(headers.firstValue("x-float"), equalTo("1.5"));
        assertThat(headers.firstValue("x-double"), equalTo("2.25"));
        assertThat(headers.firstValue("x-bigint"), equalTo("170141183460469231731687303715884105727"));
        assertThat(headers.firstValue("x-bigdec"), equalTo("1.7500"));
        assertThat(headers.firstValue("x-blob"), equalTo(base64("blobby")));
        assertThat(headers.firstValue("x-media"), equalTo(base64("{\"k\":1}")));
        assertThat(headers.firstValue("x-date"), equalTo("Sun, 16 Aug 2026 01:02:03 GMT"));
        assertThat(headers.firstValue("x-epoch"), equalTo("1786842123"));
    }

    @Test
    public void writesListHeadersAsRepeatedValues() {
        var headers = generated(fullyPopulated()).headers();

        assertThat(headers.allValues("x-tags"), contains("a", "b", "c"));
    }

    @Test
    public void writesQueryParameters() {
        var query = generated(fullyPopulated()).uri().getQuery();

        assertThat(parseQuery(query),
                equalTo(Map.of(
                        "q",
                        List.of("needle"),
                        "nums",
                        List.of("1", "2"),
                        "qtime",
                        List.of("2026-08-16T01:02:03Z"))));
    }

    @Test
    public void leavesLabelsAndBodyMembersToTheirOwners() {
        var request = generated(fullyPopulated());

        assertThat(request.uri().getPath(), equalTo("/op/id-1"));
        assertThat(utf8(request.body().asByteBuffer()), equalTo("{\"note\":\"hi\"}"));
    }

    @Test
    public void omitsAbsentMembers() {
        var request = generated(new Bound.Builder().id("id-1").text("plain").build());

        assertThat(request.headers().firstValue("x-text"), equalTo("plain"));
        assertThat(request.headers().firstValue("x-flag"), nullValue());
        assertThat(request.headers().firstValue("x-tags"), nullValue());
        assertThat(request.headers().firstValue("x-date"), nullValue());
        assertThat(request.uri().getQuery(), nullValue());
    }

    @Test
    public void omitsEmptyListHeaders() {
        var request = generated(populated(b -> b.tags(List.of()).nums(List.of())));

        assertThat(request.headers().firstValue("x-tags"), nullValue());
        assertThat(parseQuery(request.uri().getQuery()),
                equalTo(Map.of(
                        "q",
                        List.of("needle"),
                        "qtime",
                        List.of("2026-08-16T01:02:03Z"))));
    }

    @Test
    public void generatedBindingsMatchInterpretedBindings() {
        var backend = new HttpBindingRuntimeCodegenBackend(false);
        var plan = RuntimeCodecPlan.analyze(
                Bound.SCHEMA,
                backend.budgets(),
                backend.mode(),
                backend.memberSelector());
        assertThat(plan.rootStructure().writerChunks().size(), greaterThan(1));

        for (var input : List.of(fullyPopulated(), populated(b -> b.text(null).tags(List.of("solo"))))) {
            var generatedRequest = generated(input);
            var interpretedRequest = interpreted(input);

            assertThat(headerMap(generatedRequest.headers()), equalTo(headerMap(interpretedRequest.headers())));
            assertThat(
                    parseQuery(generatedRequest.uri().getQuery()),
                    equalTo(parseQuery(interpretedRequest.uri().getQuery())));
            assertThat(generatedRequest.uri().getPath(), equalTo(interpretedRequest.uri().getPath()));
            assertThat(
                    utf8(generatedRequest.body().asByteBuffer()),
                    equalTo(utf8(interpretedRequest.body().asByteBuffer())));
        }
    }

    @Test
    @Tag("runtime-codegen-strict")
    public void boundShapesUseGeneratedWriters() {
        var writer = requestWriter(Bound.SCHEMA);
        assertThat(writer, notNullValue());
        assertThat(writer.getClass().isHidden(), equalTo(true));
    }

    @Test
    public void rejectsHeaderInjectionThroughStrings() {
        var injected = populated(b -> b.text("ok\r\nX-Injected: yes"));

        assertThrows(IllegalArgumentException.class, () -> generated(injected));
        assertThrows(IllegalArgumentException.class, () -> interpreted(injected));
    }

    @Test
    public void rejectsHeaderValuesOutsideObsText() {
        var invalid = populated(b -> b.text("not-http-\u20ac"));

        assertThrows(IllegalArgumentException.class, () -> generated(invalid));
        assertThrows(IllegalArgumentException.class, () -> interpreted(invalid));
    }

    @Test
    public void allowsLatin1ObsTextInHeaders() {
        var input = populated(b -> b.text("caf\u00e9"));

        assertThat(generated(input).headers().firstValue("x-text"), equalTo("caf\u00e9"));
        assertThat(interpreted(input).headers().firstValue("x-text"), equalTo("caf\u00e9"));
    }

    @Test
    public void rejectsHeaderInjectionThroughListElements() {
        var injected = populated(b -> b.tags(List.of("fine", "bad\nX-Injected: yes")));

        assertThrows(IllegalArgumentException.class, () -> generated(injected));
        assertThrows(IllegalArgumentException.class, () -> interpreted(injected));
    }

    @Test
    public void rejectsHeaderInjectionThroughEnums() {
        var injected = populated(b -> b.mood(new Mood("bad\r\nX-Injected: yes")));

        assertThrows(IllegalArgumentException.class, () -> generated(injected));
        assertThrows(IllegalArgumentException.class, () -> interpreted(injected));
    }

    @Test
    public void trimsWhitespaceFromCallerSuppliedHeaders() {
        var padded = populated(b -> b.text("  padded  "));

        assertThat(generated(padded).headers().firstValue("x-text"), equalTo("padded"));
        assertThat(interpreted(padded).headers().firstValue("x-text"), equalTo("padded"));
    }

    @Test
    public void writesEnumsByValue() {
        var headers = generated(populated(b -> b.mood(new Mood("happy")).level(new Level(42)))).headers();

        assertThat(headers.firstValue("x-mood"), equalTo("happy"));
        assertThat(headers.firstValue("x-level"), equalTo("42"));
    }

    @Test
    public void writesTheResponseStatus() {
        assertThat(response(new Status(204, "hi"), true).statusCode(), equalTo(204));
        assertThat(response(new Status(204, "hi"), false).statusCode(), equalTo(204));
        assertThat(utf8(generated(Status.OPERATION, new Status(204, "hi")).body().asByteBuffer()),
                equalTo("{\"code\":204,\"note\":\"hi\"}"));
    }

    @Test
    @Tag("runtime-codegen-strict")
    public void generatesEveryValidBindingKind() {
        assertThat(requestWriter(WithPayload.SCHEMA), notNullValue());
        assertThat(requestWriter(WithPrefixHeaders.SCHEMA), notNullValue());
        assertThat(requestWriter(WithQueryParams.SCHEMA), notNullValue());
        assertThat(requestWriter(WithSparseHeaderList.SCHEMA), notNullValue());
    }

    @Test
    public void generatedPrefixHeadersWriteTheirBindings() {
        var request =
                generated(WithPrefixHeaders.OPERATION, new WithPrefixHeaders("tok", Map.of("a", " 1\t")));

        assertThat(request.headers().firstValue("x-token"), equalTo("tok"));
        assertThat(request.headers().firstValue("x-meta-a"), equalTo("1"));
    }

    @Test
    public void generatedQueryParamsWriteTheirBindings() {
        var request = generated(
                WithQueryParams.OPERATION,
                new WithQueryParams("tok", Map.of("one", "1", "spaced", "hello world")));

        assertThat(request.headers().firstValue("x-token"), equalTo("tok"));
        assertThat(parseQuery(request.uri().getQuery()),
                equalTo(Map.of(
                        "one",
                        List.of("1"),
                        "spaced",
                        List.of("hello world"))));
    }

    @Test
    public void generatedPayloadWritesTheBody() {
        var request = generated(WithPayload.OPERATION, new WithPayload("tok", "payload"));

        assertThat(request.headers().firstValue("x-token"), equalTo("tok"));
        assertThat(utf8(request.body().asByteBuffer()), equalTo("payload"));
    }

    @Test
    public void generatedBlobPayloadWritesTheBody() {
        var request = generated(
                WithBlobPayload.OPERATION,
                new WithBlobPayload(ByteBuffer.wrap("blob".getBytes(StandardCharsets.UTF_8))));

        assertThat(utf8(request.body().asByteBuffer()), equalTo("blob"));
        assertThat(request.headers().firstValue("content-type"), equalTo("application/octet-stream"));
    }

    @Test
    public void generatedQueryParamsWriteListValues() {
        var request = generated(
                WithQueryListParams.OPERATION,
                new WithQueryListParams(Map.of("tag", List.of("a", "b"))));

        assertThat(parseQuery(request.uri().getQuery()), equalTo(Map.of("tag", List.of("a", "b"))));
    }

    @Test
    public void generatedSparseHeaderListsWriteValues() {
        var request = generated(
                WithSparseHeaderList.OPERATION,
                new WithSparseHeaderList(List.of("a", "b")));

        assertThat(request.headers().allValues("x-tags"), contains("a", "b"));
    }

    @Test
    public void generatedSparseHeaderListsRejectNulls() {
        var input = new WithSparseHeaderList(Arrays.asList("a", null, "b"));

        assertThrows(IllegalStateException.class, () -> generated(WithSparseHeaderList.OPERATION, input));
        assertThrows(IllegalStateException.class, () -> request(WithSparseHeaderList.OPERATION, input, false));
    }

    @Test
    @Tag("runtime-codegen-strict")
    public void strictModeGeneratesDynamicBindings() {
        var strict = HttpBinding.builder().runtimeCodegen(RuntimeCodegenMode.STRICT).build();
        var request = strict.requestSerializer()
                .operation(WithPrefixHeaders.OPERATION)
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .shapeValue(new WithPrefixHeaders("tok", Map.of("a", "1")))
                .endpoint(ENDPOINT)
                .omitEmptyPayload(true)
                .serializeRequest();

        assertThat(request.headers().firstValue("x-token"), equalTo("tok"));
        assertThat(request.headers().firstValue("x-meta-a"), equalTo("1"));

        var bound = strict.requestSerializer()
                .operation(Bound.OPERATION)
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .shapeValue(populated(b -> b))
                .endpoint(ENDPOINT)
                .omitEmptyPayload(true)
                .serializeRequest();
        assertThat(bound.headers().firstValue("x-text"), equalTo("plain"));
    }

    @Test
    public void generatedPrefixHeadersRejectInjection() {
        var input = new WithPrefixHeaders("tok", Map.of("a", "safe\r\ninjected: yes"));

        assertThrows(IllegalArgumentException.class, () -> generated(WithPrefixHeaders.OPERATION, input));
        assertThrows(IllegalArgumentException.class, () -> request(WithPrefixHeaders.OPERATION, input, false));
    }

    @Test
    public void bodyOnlyShapesGetAnEmptyWriter() {
        var writer = requestWriter(BodyOnly.SCHEMA);

        assertThat(writer, notNullValue());
        assertThat(writer.getClass().isHidden(), equalTo(true));
        var request = generated(BodyOnly.OPERATION, new BodyOnly("hi"));
        assertThat(utf8(request.body().asByteBuffer()), equalTo("{\"note\":\"hi\"}"));
    }

    private static HttpBindingWriter requestWriter(Schema schema) {
        return HttpBindingSchemaExtensions.structBindingsOf(schema).request().bindingWriter(schema, false);
    }

    private static Bound fullyPopulated() {
        return populated(b -> b);
    }

    private static Bound populated(UnaryOperator<Bound.Builder> customize) {
        var builder = new Bound.Builder()
                .id("id-1")
                .text("plain")
                .media("{\"k\":1}")
                .tags(List.of("a", "b", "c"))
                .flag(true)
                .byteValue((byte) 7)
                .shortValue((short) -9)
                .intValue(11)
                .longValue(12345678901L)
                .floatValue(1.5f)
                .doubleValue(2.25d)
                .bigInt(new BigInteger("170141183460469231731687303715884105727"))
                .bigDec(new BigDecimal("1.7500"))
                .blob(ByteBuffer.wrap("blobby".getBytes(StandardCharsets.UTF_8)))
                .date(WHEN)
                .epoch(WHEN)
                .q("needle")
                .nums(List.of(1, 2))
                .qtime(WHEN)
                .note("hi");
        return customize.apply(builder).build();
    }

    private static HttpRequest generated(SerializableStruct input) {
        return generated(Bound.OPERATION, input);
    }

    private static HttpRequest interpreted(SerializableStruct input) {
        return request(Bound.OPERATION, input, false);
    }

    private static HttpRequest generated(ApiOperation<?, ?> operation, SerializableStruct input) {
        return request(operation, input, true);
    }

    private static HttpRequest request(ApiOperation<?, ?> operation, SerializableStruct input, boolean codegen) {
        return binding(codegen).requestSerializer()
                .operation(operation)
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .shapeValue(input)
                .endpoint(ENDPOINT)
                .omitEmptyPayload(true)
                .serializeRequest();
    }

    private static HttpResponse response(SerializableStruct output, boolean codegen) {
        return binding(codegen).responseSerializer()
                .operation(Status.OPERATION)
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .shapeValue(output)
                .serializeResponse();
    }

    private static HttpBinding binding(boolean codegen) {
        var builder = HttpBinding.builder()
                .runtimeCodegen(codegen ? RuntimeCodegenMode.ENABLED : RuntimeCodegenMode.DISABLED);
        if (codegen && !builder.resolvedRuntimeCodegen()) {
            throw new IllegalStateException("Runtime codegen unavailable on " + Runtime.version());
        }
        return builder.build();
    }

    private static Map<String, List<String>> headerMap(HttpHeaders headers) {
        Map<String, List<String>> result = new TreeMap<>();
        headers.forEachEntry(result,
                (map, name, value) -> map.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(value));
        return result;
    }

    private static Map<String, List<String>> parseQuery(String query) {
        Map<String, List<String>> result = new TreeMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : urlDecode(pair.substring(eq + 1));
            result.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String utf8(ByteBuffer buffer) {
        var bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public record Mood(String value) implements SmithyEnum {
        static final Schema SCHEMA = Schema.createEnum(
                ShapeId.from("smithy.example#Mood"),
                Set.of("happy", "sad"),
                Mood.class);

        @Override
        public String getValue() {
            return value;
        }
    }

    public record Level(int value) implements SmithyIntEnum {
        static final Schema SCHEMA = Schema.createIntEnum(
                ShapeId.from("smithy.example#Level"),
                Set.of(1, 42),
                Level.class);

        @Override
        public int getValue() {
            return value;
        }
    }

    public static final class Bound implements SerializableStruct {

        private static final Schema TAGS = Schema.listBuilder(ShapeId.from("smithy.example#Tags"))
                .putMember("member", PreludeSchemas.STRING)
                .build();
        private static final Schema NUMS = Schema.listBuilder(ShapeId.from("smithy.example#Nums"))
                .putMember("member", PreludeSchemas.INTEGER)
                .build();
        private static final Schema JSON_STRING = Schema.createString(
                ShapeId.from("smithy.example#JsonString"),
                new MediaTypeTrait("application/json"));

        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#Bound"))
                .shapeClass(Bound.class)
                .builderSupplier(Builder::new)
                .putMember("id", PreludeSchemas.STRING, new HttpLabelTrait(), new RequiredTrait())
                .putMember("text", PreludeSchemas.STRING, new HttpHeaderTrait("x-text"))
                .putMember("media", JSON_STRING, new HttpHeaderTrait("x-media"))
                .putMember("tags", TAGS, new HttpHeaderTrait("x-tags"))
                .putMember("flag", PreludeSchemas.BOOLEAN, new HttpHeaderTrait("x-flag"))
                .putMember("byteValue", PreludeSchemas.BYTE, new HttpHeaderTrait("x-byte"))
                .putMember("shortValue", PreludeSchemas.SHORT, new HttpHeaderTrait("x-short"))
                .putMember("intValue", PreludeSchemas.INTEGER, new HttpHeaderTrait("x-int"))
                .putMember("longValue", PreludeSchemas.LONG, new HttpHeaderTrait("x-long"))
                .putMember("floatValue", PreludeSchemas.FLOAT, new HttpHeaderTrait("x-float"))
                .putMember("doubleValue", PreludeSchemas.DOUBLE, new HttpHeaderTrait("x-double"))
                .putMember("bigInt", PreludeSchemas.BIG_INTEGER, new HttpHeaderTrait("x-bigint"))
                .putMember("bigDec", PreludeSchemas.BIG_DECIMAL, new HttpHeaderTrait("x-bigdec"))
                .putMember("blob", PreludeSchemas.BLOB, new HttpHeaderTrait("x-blob"))
                .putMember("date", PreludeSchemas.TIMESTAMP, new HttpHeaderTrait("x-date"))
                .putMember(
                        "epoch",
                        PreludeSchemas.TIMESTAMP,
                        new HttpHeaderTrait("x-epoch"),
                        new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS))
                .putMember("mood", Mood.SCHEMA, new HttpHeaderTrait("x-mood"))
                .putMember("level", Level.SCHEMA, new HttpHeaderTrait("x-level"))
                .putMember("q", PreludeSchemas.STRING, new HttpQueryTrait("q"))
                .putMember("nums", NUMS, new HttpQueryTrait("nums"))
                .putMember("qtime", PreludeSchemas.TIMESTAMP, new HttpQueryTrait("qtime"))
                .putMember("note", PreludeSchemas.STRING)
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("BoundOperation", SCHEMA, "/op/{id}");

        private final String id;
        private final String text;
        private final String media;
        private final List<String> tags;
        private final Boolean flag;
        private final Byte byteValue;
        private final Short shortValue;
        private final Integer intValue;
        private final Long longValue;
        private final Float floatValue;
        private final Double doubleValue;
        private final BigInteger bigInt;
        private final BigDecimal bigDec;
        private final ByteBuffer blob;
        private final Instant date;
        private final Instant epoch;
        private final Mood mood;
        private final Level level;
        private final String q;
        private final List<Integer> nums;
        private final Instant qtime;
        private final String note;

        @SuppressWarnings("checkstyle:ParameterNumber")
        Bound(
                String id,
                String text,
                String media,
                List<String> tags,
                Boolean flag,
                Byte byteValue,
                Short shortValue,
                Integer intValue,
                Long longValue,
                Float floatValue,
                Double doubleValue,
                BigInteger bigInt,
                BigDecimal bigDec,
                ByteBuffer blob,
                Instant date,
                Instant epoch,
                Mood mood,
                Level level,
                String q,
                List<Integer> nums,
                Instant qtime,
                String note
        ) {
            this.id = id;
            this.text = text;
            this.media = media;
            this.tags = tags;
            this.flag = flag;
            this.byteValue = byteValue;
            this.shortValue = shortValue;
            this.intValue = intValue;
            this.longValue = longValue;
            this.floatValue = floatValue;
            this.doubleValue = doubleValue;
            this.bigInt = bigInt;
            this.bigDec = bigDec;
            this.blob = blob;
            this.date = date;
            this.epoch = epoch;
            this.mood = mood;
            this.level = level;
            this.q = q;
            this.nums = nums;
            this.qtime = qtime;
            this.note = note;
        }

        public String id() {
            return id;
        }

        public String text() {
            return text;
        }

        public String media() {
            return media;
        }

        public List<String> tags() {
            return tags;
        }

        public Boolean flag() {
            return flag;
        }

        public Byte byteValue() {
            return byteValue;
        }

        public Short shortValue() {
            return shortValue;
        }

        public Integer intValue() {
            return intValue;
        }

        public Long longValue() {
            return longValue;
        }

        public Float floatValue() {
            return floatValue;
        }

        public Double doubleValue() {
            return doubleValue;
        }

        public BigInteger bigInt() {
            return bigInt;
        }

        public BigDecimal bigDec() {
            return bigDec;
        }

        public ByteBuffer blob() {
            return blob;
        }

        public Instant date() {
            return date;
        }

        public Instant epoch() {
            return epoch;
        }

        public Mood mood() {
            return mood;
        }

        public Level level() {
            return level;
        }

        public String q() {
            return q;
        }

        public List<Integer> nums() {
            return nums;
        }

        public Instant qtime() {
            return qtime;
        }

        public String note() {
            return note;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (id != null) {
                serializer.writeString(SCHEMA.member("id"), id);
            }
            if (text != null) {
                serializer.writeString(SCHEMA.member("text"), text);
            }
            if (media != null) {
                serializer.writeString(SCHEMA.member("media"), media);
            }
            if (tags != null) {
                serializer.writeList(SCHEMA.member("tags"), tags, tags.size(), (values, ser) -> {
                    for (String value : values) {
                        ser.writeString(TAGS.listMember(), value);
                    }
                });
            }
            if (flag != null) {
                serializer.writeBoolean(SCHEMA.member("flag"), flag);
            }
            if (byteValue != null) {
                serializer.writeByte(SCHEMA.member("byteValue"), byteValue);
            }
            if (shortValue != null) {
                serializer.writeShort(SCHEMA.member("shortValue"), shortValue);
            }
            if (intValue != null) {
                serializer.writeInteger(SCHEMA.member("intValue"), intValue);
            }
            if (longValue != null) {
                serializer.writeLong(SCHEMA.member("longValue"), longValue);
            }
            if (floatValue != null) {
                serializer.writeFloat(SCHEMA.member("floatValue"), floatValue);
            }
            if (doubleValue != null) {
                serializer.writeDouble(SCHEMA.member("doubleValue"), doubleValue);
            }
            if (bigInt != null) {
                serializer.writeBigInteger(SCHEMA.member("bigInt"), bigInt);
            }
            if (bigDec != null) {
                serializer.writeBigDecimal(SCHEMA.member("bigDec"), bigDec);
            }
            if (blob != null) {
                serializer.writeBlob(SCHEMA.member("blob"), blob);
            }
            if (date != null) {
                serializer.writeTimestamp(SCHEMA.member("date"), date);
            }
            if (epoch != null) {
                serializer.writeTimestamp(SCHEMA.member("epoch"), epoch);
            }
            if (mood != null) {
                serializer.writeString(SCHEMA.member("mood"), mood.getValue());
            }
            if (level != null) {
                serializer.writeInteger(SCHEMA.member("level"), level.getValue());
            }
            if (q != null) {
                serializer.writeString(SCHEMA.member("q"), q);
            }
            if (nums != null) {
                serializer.writeList(SCHEMA.member("nums"), nums, nums.size(), (values, ser) -> {
                    for (Integer value : values) {
                        ser.writeInteger(NUMS.listMember(), value);
                    }
                });
            }
            if (qtime != null) {
                serializer.writeTimestamp(SCHEMA.member("qtime"), qtime);
            }
            if (note != null) {
                serializer.writeString(SCHEMA.member("note"), note);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "id" -> id;
                case "text" -> text;
                case "media" -> media;
                case "tags" -> tags;
                case "flag" -> flag;
                case "byteValue" -> byteValue;
                case "shortValue" -> shortValue;
                case "intValue" -> intValue;
                case "longValue" -> longValue;
                case "floatValue" -> floatValue;
                case "doubleValue" -> doubleValue;
                case "bigInt" -> bigInt;
                case "bigDec" -> bigDec;
                case "blob" -> blob;
                case "date" -> date;
                case "epoch" -> epoch;
                case "mood" -> mood;
                case "level" -> level;
                case "q" -> q;
                case "nums" -> nums;
                case "qtime" -> qtime;
                case "note" -> note;
                default -> null;
            };
        }

        public static final class Builder implements ShapeBuilder<Bound> {
            private String id;
            private String text;
            private String media;
            private List<String> tags;
            private Boolean flag;
            private Byte byteValue;
            private Short shortValue;
            private Integer intValue;
            private Long longValue;
            private Float floatValue;
            private Double doubleValue;
            private BigInteger bigInt;
            private BigDecimal bigDec;
            private ByteBuffer blob;
            private Instant date;
            private Instant epoch;
            private Mood mood;
            private Level level;
            private String q;
            private List<Integer> nums;
            private Instant qtime;
            private String note;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder text(String text) {
                this.text = text;
                return this;
            }

            public Builder media(String media) {
                this.media = media;
                return this;
            }

            public Builder tags(List<String> tags) {
                this.tags = tags;
                return this;
            }

            public Builder flag(Boolean flag) {
                this.flag = flag;
                return this;
            }

            public Builder byteValue(Byte byteValue) {
                this.byteValue = byteValue;
                return this;
            }

            public Builder shortValue(Short shortValue) {
                this.shortValue = shortValue;
                return this;
            }

            public Builder intValue(Integer intValue) {
                this.intValue = intValue;
                return this;
            }

            public Builder longValue(Long longValue) {
                this.longValue = longValue;
                return this;
            }

            public Builder floatValue(Float floatValue) {
                this.floatValue = floatValue;
                return this;
            }

            public Builder doubleValue(Double doubleValue) {
                this.doubleValue = doubleValue;
                return this;
            }

            public Builder bigInt(BigInteger bigInt) {
                this.bigInt = bigInt;
                return this;
            }

            public Builder bigDec(BigDecimal bigDec) {
                this.bigDec = bigDec;
                return this;
            }

            public Builder blob(ByteBuffer blob) {
                this.blob = blob;
                return this;
            }

            public Builder date(Instant date) {
                this.date = date;
                return this;
            }

            public Builder epoch(Instant epoch) {
                this.epoch = epoch;
                return this;
            }

            public Builder mood(Mood mood) {
                this.mood = mood;
                return this;
            }

            public Builder level(Level level) {
                this.level = level;
                return this;
            }

            public Builder q(String q) {
                this.q = q;
                return this;
            }

            public Builder nums(List<Integer> nums) {
                this.nums = nums;
                return this;
            }

            public Builder qtime(Instant qtime) {
                this.qtime = qtime;
                return this;
            }

            public Builder note(String note) {
                this.note = note;
                return this;
            }

            @Override
            public Bound build() {
                return new Bound(
                        id,
                        text,
                        media,
                        tags,
                        flag,
                        byteValue,
                        shortValue,
                        intValue,
                        longValue,
                        floatValue,
                        doubleValue,
                        bigInt,
                        bigDec,
                        blob,
                        date,
                        epoch,
                        mood,
                        level,
                        q,
                        nums,
                        qtime,
                        note);
            }

            @Override
            public ShapeBuilder<Bound> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public record Status(Integer code, String note) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#Status"))
                .shapeClass(Status.class)
                .builderSupplier(Builder::new)
                .putMember("code", PreludeSchemas.INTEGER, new HttpResponseCodeTrait())
                .putMember("note", PreludeSchemas.STRING)
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("StatusOperation", SCHEMA, "/status");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (code != null) {
                serializer.writeInteger(SCHEMA.member("code"), code);
            }
            if (note != null) {
                serializer.writeString(SCHEMA.member("note"), note);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "code" -> code;
                case "note" -> note;
                default -> null;
            };
        }

        public static final class Builder implements ShapeBuilder<Status> {
            private Integer code;
            private String note;

            public Builder code(Integer code) {
                this.code = code;
                return this;
            }

            public Builder note(String note) {
                this.note = note;
                return this;
            }

            @Override
            public Status build() {
                return new Status(code, note);
            }

            @Override
            public ShapeBuilder<Status> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public record BodyOnly(String note) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#BodyOnly"))
                .shapeClass(BodyOnly.class)
                .builderSupplier(Builder::new)
                .putMember("note", PreludeSchemas.STRING)
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("BodyOnlyOperation", SCHEMA, "/body");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (note != null) {
                serializer.writeString(SCHEMA.member("note"), note);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "note".equals(member.memberName()) ? (T) note : null;
        }

        public static final class Builder implements ShapeBuilder<BodyOnly> {
            private String note;

            public Builder note(String note) {
                this.note = note;
                return this;
            }

            @Override
            public BodyOnly build() {
                return new BodyOnly(note);
            }

            @Override
            public ShapeBuilder<BodyOnly> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public record WithPayload(String token, String body) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#WithPayload"))
                .shapeClass(WithPayload.class)
                .putMember("token", PreludeSchemas.STRING, new HttpHeaderTrait("x-token"))
                .putMember("body", PreludeSchemas.STRING, new HttpPayloadTrait())
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("PayloadOperation", SCHEMA, "/payload");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (token != null) {
                serializer.writeString(SCHEMA.member("token"), token);
            }
            if (body != null) {
                serializer.writeString(SCHEMA.member("body"), body);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "token" -> token;
                case "body" -> body;
                default -> null;
            };
        }
    }

    public record WithPrefixHeaders(String token, Map<String, String> meta) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#WithPrefixHeaders"))
                .shapeClass(WithPrefixHeaders.class)
                .putMember("token", PreludeSchemas.STRING, new HttpHeaderTrait("x-token"))
                .putMember("meta", STRING_MAP, new HttpPrefixHeadersTrait("x-meta-"))
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("PrefixOperation", SCHEMA, "/prefix");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (token != null) {
                serializer.writeString(SCHEMA.member("token"), token);
            }
            if (meta != null) {
                serializer.writeMap(SCHEMA.member("meta"), meta, meta.size(), (values, mapSerializer) -> {
                    for (var entry : values.entrySet()) {
                        mapSerializer.writeEntry(
                                STRING_MAP.mapKeyMember(),
                                entry.getKey(),
                                entry.getValue(),
                                (value, ser) -> ser.writeString(STRING_MAP.mapValueMember(), value));
                    }
                });
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "token" -> token;
                case "meta" -> meta;
                default -> null;
            };
        }
    }

    public record WithBlobPayload(ByteBuffer body) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#WithBlobPayload"))
                .shapeClass(WithBlobPayload.class)
                .putMember("body", PreludeSchemas.BLOB, new HttpPayloadTrait())
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("BlobPayloadOperation", SCHEMA, "/blob-payload");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (body != null) {
                serializer.writeBlob(SCHEMA.member("body"), body);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "body".equals(member.memberName()) ? (T) body : null;
        }
    }

    public record WithQueryParams(String token, Map<String, String> params) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#WithQueryParams"))
                .shapeClass(WithQueryParams.class)
                .putMember("token", PreludeSchemas.STRING, new HttpHeaderTrait("x-token"))
                .putMember("params", STRING_MAP, new HttpQueryParamsTrait())
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("QueryParamsOperation", SCHEMA, "/query-params");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (token != null) {
                serializer.writeString(SCHEMA.member("token"), token);
            }
            if (params != null) {
                serializer.writeMap(SCHEMA.member("params"), params, params.size(), (values, mapSerializer) -> {
                    for (var entry : values.entrySet()) {
                        mapSerializer.writeEntry(
                                STRING_MAP.mapKeyMember(),
                                entry.getKey(),
                                entry.getValue(),
                                (value, ser) -> ser.writeString(STRING_MAP.mapValueMember(), value));
                    }
                });
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "token" -> token;
                case "params" -> params;
                default -> null;
            };
        }
    }

    public record WithQueryListParams(Map<String, List<String>> params) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#WithQueryListParams"))
                .shapeClass(WithQueryListParams.class)
                .putMember("params", STRING_LIST_MAP, new HttpQueryParamsTrait())
                .build();
        static final ApiOperation<?, ?> OPERATION =
                operation("QueryListParamsOperation", SCHEMA, "/query-list-params");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (params != null) {
                serializer.writeMap(SCHEMA.member("params"), params, params.size(), (values, mapSerializer) -> {
                    for (var entry : values.entrySet()) {
                        mapSerializer.writeEntry(
                                STRING_LIST_MAP.mapKeyMember(),
                                entry.getKey(),
                                entry.getValue(),
                                (value, ser) -> ser.writeList(
                                        STRING_LIST_MAP.mapValueMember(),
                                        value,
                                        value.size(),
                                        (elements, elementSerializer) -> {
                                            for (String element : elements) {
                                                elementSerializer.writeString(STRING_LIST.listMember(), element);
                                            }
                                        }));
                    }
                });
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "params".equals(member.memberName()) ? (T) params : null;
        }
    }

    public record WithSparseHeaderList(List<String> tags) implements SerializableStruct {
        private static final Schema SPARSE_TAGS = Schema
                .listBuilder(ShapeId.from("smithy.example#SparseTags"), new SparseTrait())
                .putMember("member", PreludeSchemas.STRING)
                .build();
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#WithSparseHeaderList"))
                .shapeClass(WithSparseHeaderList.class)
                .putMember("tags", SPARSE_TAGS, new HttpHeaderTrait("x-tags"))
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("SparseHeadersOperation", SCHEMA, "/sparse");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (tags != null) {
                serializer.writeList(SCHEMA.member("tags"), tags, tags.size(), (values, elementSerializer) -> {
                    for (String value : values) {
                        if (value == null) {
                            elementSerializer.writeNull(SPARSE_TAGS.listMember());
                        } else {
                            elementSerializer.writeString(SPARSE_TAGS.listMember(), value);
                        }
                    }
                });
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "tags".equals(member.memberName()) ? (T) tags : null;
        }
    }

    private static final Schema STRING_MAP = Schema.mapBuilder(ShapeId.from("smithy.example#StringMap"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema STRING_LIST = Schema.listBuilder(ShapeId.from("smithy.example#StringList"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
    private static final Schema STRING_LIST_MAP =
            Schema.mapBuilder(ShapeId.from("smithy.example#StringListMap"))
                    .putMember("key", PreludeSchemas.STRING)
                    .putMember("value", STRING_LIST)
                    .build();

    private static ApiOperation<?, ?> operation(String name, Schema inputSchema, String uri) {
        var httpTrait = HttpTrait.builder().method("POST").uri(UriPattern.parse(uri)).code(200).build();
        var operationSchema = Schema.createOperation(ShapeId.from("smithy.example#" + name), httpTrait);
        return new ApiOperation<>() {
            @Override
            public ShapeBuilder<SerializableStruct> inputBuilder() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ShapeBuilder<SerializableStruct> outputBuilder() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return operationSchema;
            }

            @Override
            public Schema inputSchema() {
                return inputSchema;
            }

            @Override
            public Schema outputSchema() {
                return inputSchema;
            }

            @Override
            public TypeRegistry errorRegistry() {
                return TypeRegistry.builder().build();
            }

            @Override
            public List<ShapeId> effectiveAuthSchemes() {
                return List.of();
            }

            @Override
            public List<Schema> errorSchemas() {
                return List.of();
            }

            @Override
            public ApiService service() {
                return null;
            }
        };
    }
}
