/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.CallContext;
import software.amazon.smithy.java.runtime.client.core.settings.ClockSetting;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.retries.api.RetrySafety;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ReadonlyTrait;

public class HttpRetryErrorClassifierTest {

    private ApiOperation<?, ?> createOperation(Schema operationSchema) {
        return new ApiOperation<SerializableStruct, SerializableStruct>() {
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
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema outputSchema() {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeRegistry typeRegistry() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<ShapeId> effectiveAuthSchemes() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    public void appliesModelInformation() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"), new ReadonlyTrait());
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder().statusCode(400).build();
        var e = new ApiException("err");
        var context = Context.create();

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
    }

    @Test
    public void appliesRetryAfterHeader() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder()
            .statusCode(500)
            .headers(HttpHeaders.of(Map.of("retry-after", List.of("10"))))
            .build();
        var e = new ApiException("err");
        var context = Context.create();

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), equalTo(Duration.ofSeconds(10)));
    }

    @Test
    public void appliesRetryAfterHeaderDate() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder()
            .statusCode(500)
            .headers(HttpHeaders.of(Map.of("retry-after", List.of("Wed, 21 Oct 2015 07:28:00 GMT"))))
            .build();
        var e = new ApiException("err");
        var context = Context.create();
        context.put(ClockSetting.CLOCK, Clock.fixed(Instant.parse("2015-10-21T05:28:00Z"), ZoneId.of("UTC")));

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), equalTo(Duration.ofHours(2)));
    }

    @Test
    public void appliesThrottlingStatusCode503() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder().statusCode(503).build();
        var e = new ApiException("err");
        var context = Context.create();

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void appliesThrottlingStatusCode429() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder().statusCode(429).build();
        var e = new ApiException("err");
        var context = Context.create();

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void retriesSafe5xx() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder().statusCode(500).build();
        var e = new ApiException("err");
        var context = Context.create();
        context.put(CallContext.IDEMPOTENCY_TOKEN, "foo");

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(false));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void doesNotRetryUnsafe5xx() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder().statusCode(500).build();
        var e = new ApiException("err");
        var context = Context.create();

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.NO));
        assertThat(e.isThrottle(), is(false));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void doesNotRetryNormal4xx() {
        var schema = Schema.createOperation(ShapeId.from("com#Foo"));
        var operation = createOperation(schema);
        var response = SmithyHttpResponse.builder().statusCode(400).build();
        var e = new ApiException("err");
        var context = Context.create();

        HttpRetryErrorClassifier.applyRetryInfo(operation, response, e, context);
        ApiOperation.applyRetryInfoFromModel(schema, e);

        assertThat(e.isRetrySafe(), is(RetrySafety.NO));
        assertThat(e.isThrottle(), is(false));
        assertThat(e.retryAfter(), nullValue());
    }
}
