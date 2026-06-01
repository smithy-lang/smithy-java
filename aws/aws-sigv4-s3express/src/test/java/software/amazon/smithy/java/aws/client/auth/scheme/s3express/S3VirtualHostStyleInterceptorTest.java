/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.model.shapes.ShapeId;

class S3VirtualHostStyleInterceptorTest {

    @Test
    void stripsBucketOnlyAtPathSegmentBoundary() {
        var request = request("/bucket/key");
        var result = intercept(request, "bucket");

        assertThat(result.uri().getPath(), equalTo("/key"));
    }

    @Test
    void doesNotStripBucketPrefixFromLongerPathSegment() {
        var request = request("/bucket-extra/key");
        var result = intercept(request, "bucket");

        assertThat(result, sameInstance(request));
    }

    @Test
    void stripsBucketOnlyPathToRoot() {
        var result = intercept(request("/bucket"), "bucket");

        assertThat(result.uri().getPath(), equalTo("/"));
    }

    @Test
    void doesNotStripNonLeadingBucketSegment() {
        var request = request("/prefix/bucket/key");
        var result = intercept(request, "bucket");

        assertThat(result, sameInstance(request));
    }

    @Test
    void doesNotStripEmptyPath() {
        var request = request("");
        var result = intercept(request, "bucket");

        assertThat(result, sameInstance(request));
    }

    private static HttpRequest intercept(HttpRequest request, String bucket) {
        var context = Context.create();
        context.put(S3ExpressContext.BUCKET, bucket);
        var hook = new RequestHook<>(operation(), context, new Input(), request);
        return S3VirtualHostStyleInterceptor.INSTANCE.modifyBeforeSigning(hook);
    }

    private static HttpRequest request(String path) {
        return HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://example.com" + path))
                .toUnmodifiable();
    }

    private static ApiOperation<Input, Input> operation() {
        return new ApiOperation<>() {
            @Override
            public ShapeBuilder<Input> inputBuilder() {
                return null;
            }

            @Override
            public ShapeBuilder<Input> outputBuilder() {
                return null;
            }

            @Override
            public Schema schema() {
                return null;
            }

            @Override
            public Schema inputSchema() {
                return null;
            }

            @Override
            public Schema outputSchema() {
                return null;
            }

            @Override
            public TypeRegistry errorRegistry() {
                return null;
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

    private static final class Input implements SerializableStruct {
        @Override
        public Schema schema() {
            return null;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
