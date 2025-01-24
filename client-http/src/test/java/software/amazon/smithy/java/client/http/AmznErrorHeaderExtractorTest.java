/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.model.shapes.ShapeId;

public class AmznErrorHeaderExtractorTest {
    @Test
    public void checksForHeaderPresence() {
        var extractor = new AmznErrorHeaderExtractor();

        var response1 = HttpResponse.builder()
                .statusCode(400)
                .headers(HttpHeaders.of(Map.of("x-amzn-errortype", List.of("foo"))))
                .build();
        var response2 = HttpResponse.builder().statusCode(400).build();

        assertThat(extractor.hasHeader(response1), is(true));
        assertThat(extractor.hasHeader(response2), is(false));
    }

    @Test
    public void resolveIdReturnsNullWhenMissing() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = HttpResponse.builder().statusCode(400).build();

        assertThat(extractor.resolveId(response, "com.foo", TypeRegistry.builder().build()), nullValue());
    }

    @Test
    public void resolvesAbsoluteShapeIds() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = HttpResponse.builder()
                .statusCode(400)
                .headers(HttpHeaders.of(Map.of("x-amzn-errortype", List.of("baz#Bam"))))
                .build();
        var registry = TypeRegistry.builder()
                .putType(ShapeId.from("baz#Bam"), ModeledException.class, ApiExceptionBuilder::new)
                .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("baz#Bam")));
    }

    @Test
    public void resolvesAbsoluteShapeIdsWithColons() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = HttpResponse.builder()
                .statusCode(400)
                .headers(
                        HttpHeaders.of(
                                Map.of(
                                        "x-amzn-errortype",
                                        List.of("baz#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/"))))
                .build();
        var registry = TypeRegistry.builder()
                .putType(ShapeId.from("baz#Bam"), ModeledException.class, ApiExceptionBuilder::new)
                .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("baz#Bam")));
    }

    @Test
    public void resolvesRelativeShapeIds() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = HttpResponse.builder()
                .statusCode(400)
                .headers(
                        HttpHeaders.of(
                                Map.of(
                                        "x-amzn-errortype",
                                        List.of("Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/"))))
                .build();
        var registry = TypeRegistry.builder()
                .putType(ShapeId.from("com.foo#Bam"), ModeledException.class, ApiExceptionBuilder::new)
                .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("com.foo#Bam")));
    }

    @Test
    public void resolvesRelativeShapeIdsWithColons() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = HttpResponse.builder()
                .statusCode(400)
                .headers(
                        HttpHeaders.of(
                                Map.of(
                                        "x-amzn-errortype",
                                        List.of("baz#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/"))))
                .build();
        var registry = TypeRegistry.builder()
                .putType(ShapeId.from("baz#Bam"), ModeledException.class, ApiExceptionBuilder::new)
                .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("baz#Bam")));
    }

    @Test
    public void resolvesToServiceErrorWhenAbsoluteNotFound() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = HttpResponse.builder()
                .statusCode(400)
                .headers(
                        HttpHeaders.of(
                                Map.of(
                                        "x-amzn-errortype",
                                        List.of("other#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/"))))
                .build();
        var registry = TypeRegistry.builder()
                .putType(ShapeId.from("com.foo#Bam"), ModeledException.class, ApiExceptionBuilder::new)
                .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("com.foo#Bam")));
    }

    @Test
    public void returnsNullWhenNoTypeFound() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = HttpResponse.builder()
                .statusCode(400)
                .headers(
                        HttpHeaders.of(
                                Map.of(
                                        "x-amzn-errortype",
                                        List.of("other#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/"))))
                .build();
        var registry = TypeRegistry.empty();

        assertThat(extractor.resolveId(response, "com.foo", registry), nullValue());
    }

    private static final class ApiExceptionBuilder implements ShapeBuilder<ModeledException> {

        @Override
        public Schema schema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModeledException build() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<ModeledException> deserialize(ShapeDeserializer decoder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<ModeledException> errorCorrection() {
            throw new UnsupportedOperationException();
        }
    }
}
