/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.model.shapes.ShapeId;

public class AmznErrorHeaderExtractorTest {
    @Test
    public void checksForHeaderPresence() {
        var extractor = new AmznErrorHeaderExtractor();

        var response1 = SmithyHttpResponse.builder()
            .headers(HttpHeaders.of(Map.of("x-amzn-errortype", List.of("foo")), (k, v) -> true))
            .build();
        var response2 = SmithyHttpResponse.builder().build();

        assertThat(extractor.hasHeader(response1), is(true));
        assertThat(extractor.hasHeader(response2), is(false));
    }

    @Test
    public void resolveIdReturnsNullWhenMissing() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = SmithyHttpResponse.builder().build();

        assertThat(extractor.resolveId(response, "com.foo", TypeRegistry.builder().build()), nullValue());
    }

    @Test
    public void resolvesAbsoluteShapeIds() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = SmithyHttpResponse.builder()
            .headers(HttpHeaders.of(Map.of("x-amzn-errortype", List.of("baz#Bam")), (k, v) -> true))
            .build();
        var registry = TypeRegistry.builder()
            .putType(ShapeId.from("baz#Bam"), ModeledApiException.class, ApiExceptionBuilder::new)
            .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("baz#Bam")));
    }

    @Test
    public void resolvesAbsoluteShapeIdsWithColons() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = SmithyHttpResponse.builder()
            .headers(
                HttpHeaders.of(
                    Map.of(
                        "x-amzn-errortype",
                        List.of("baz#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/")
                    ),
                    (k, v) -> true
                )
            )
            .build();
        var registry = TypeRegistry.builder()
            .putType(ShapeId.from("baz#Bam"), ModeledApiException.class, ApiExceptionBuilder::new)
            .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("baz#Bam")));
    }

    @Test
    public void resolvesRelativeShapeIds() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = SmithyHttpResponse.builder()
            .headers(
                HttpHeaders.of(
                    Map.of(
                        "x-amzn-errortype",
                        List.of("Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/")
                    ),
                    (k, v) -> true
                )
            )
            .build();
        var registry = TypeRegistry.builder()
            .putType(ShapeId.from("com.foo#Bam"), ModeledApiException.class, ApiExceptionBuilder::new)
            .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("com.foo#Bam")));
    }

    @Test
    public void resolvesRelativeShapeIdsWithColons() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = SmithyHttpResponse.builder()
            .headers(
                HttpHeaders.of(
                    Map.of(
                        "x-amzn-errortype",
                        List.of("baz#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/")
                    ),
                    (k, v) -> true
                )
            )
            .build();
        var registry = TypeRegistry.builder()
            .putType(ShapeId.from("baz#Bam"), ModeledApiException.class, ApiExceptionBuilder::new)
            .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("baz#Bam")));
    }

    @Test
    public void resolvesToServiceErrorWhenAbsoluteNotFound() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = SmithyHttpResponse.builder()
            .headers(
                HttpHeaders.of(
                    Map.of(
                        "x-amzn-errortype",
                        List.of("other#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/")
                    ),
                    (k, v) -> true
                )
            )
            .build();
        var registry = TypeRegistry.builder()
            .putType(ShapeId.from("com.foo#Bam"), ModeledApiException.class, ApiExceptionBuilder::new)
            .build();

        assertThat(extractor.resolveId(response, "com.foo", registry), equalTo(ShapeId.from("com.foo#Bam")));
    }

    @Test
    public void returnsNullWhenNoTypeFound() {
        var extractor = new AmznErrorHeaderExtractor();
        var response = SmithyHttpResponse.builder()
            .headers(
                HttpHeaders.of(
                    Map.of(
                        "x-amzn-errortype",
                        List.of("other#Bam:http://internal.amazon.com/coral/com.amazon.coral.validate/")
                    ),
                    (k, v) -> true
                )
            )
            .build();
        var registry = TypeRegistry.builder().build();

        assertThat(extractor.resolveId(response, "com.foo", registry), nullValue());
    }

    private static final class ApiExceptionBuilder implements ShapeBuilder<ModeledApiException> {
        @Override
        public ModeledApiException build() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<ModeledApiException> deserialize(ShapeDeserializer decoder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<ModeledApiException> errorCorrection() {
            throw new UnsupportedOperationException();
        }
    }
}
