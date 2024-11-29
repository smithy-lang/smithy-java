/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.useragent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.FeatureId;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

public class UserAgentPluginTest {
    @Test
    public void addsDefaultAgent() throws Exception {
        UserAgentPlugin.UserAgentInterceptor interceptor = new UserAgentPlugin.UserAgentInterceptor();
        var context = Context.create();
        var req = HttpRequest.builder().uri(new URI("/")).method("GET").build();
        var foo = new Foo();
        var updated = interceptor.modifyBeforeSigning(new RequestHook<>(createOperation(), context, foo, req));

        var hd = updated.headers().allValues("user-agent");

        assertThat(hd, hasSize(1));
        assertThat(hd.get(0), startsWith("smithy-java/"));
        assertThat(hd.get(0), containsString("lang/java#"));
    }

    @Test
    public void addsApplicationId() throws Exception {
        UserAgentPlugin.UserAgentInterceptor interceptor = new UserAgentPlugin.UserAgentInterceptor();
        var context = Context.create();
        context.put(CallContext.APPLICATION_ID, "hello there");
        var req = HttpRequest.builder().uri(new URI("/")).method("GET").build();
        var foo = new Foo();
        var updated = interceptor.modifyBeforeSigning(new RequestHook<>(createOperation(), context, foo, req));

        var hd = updated.headers().allValues("user-agent");

        assertThat(hd, hasSize(1));
        assertThat(hd.get(0), containsString("app/hello_there"));
    }

    private enum Features implements FeatureId {
        FOO,
        BAR,
        BAZ {
            @Override
            public String toString() {
                return "baz baz";
            }
        }
    }

    @Test
    public void addsFeatureIds() throws Exception {
        UserAgentPlugin.UserAgentInterceptor interceptor = new UserAgentPlugin.UserAgentInterceptor();
        var context = Context.create();

        Set<FeatureId> s = new LinkedHashSet<>();
        s.add(Features.FOO);
        s.add(Features.BAR);
        s.add(Features.BAZ);
        context.put(CallContext.FEATURE_IDS, s);

        var req = HttpRequest.builder().uri(new URI("/")).method("GET").build();
        var foo = new Foo();
        var updated = interceptor.modifyBeforeSigning(new RequestHook<>(createOperation(), context, foo, req));

        var hd = updated.headers().allValues("user-agent");

        assertThat(hd, hasSize(1));
        assertThat(hd.get(0), containsString("m/FOO,BAR,baz_baz"));
    }

    private static final class Foo implements SerializableStruct {
        @Override
        public Schema schema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    private ApiOperation<SerializableStruct, SerializableStruct> createOperation() {
        return new ApiOperation<>() {
            @Override
            public ShapeBuilder<SerializableStruct> inputBuilder() {
                return null;
            }

            @Override
            public ShapeBuilder<SerializableStruct> outputBuilder() {
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
            public Set<Schema> errorSchemas() {
                return Set.of();
            }

            @Override
            public TypeRegistry typeRegistry() {
                return null;
            }

            @Override
            public List<ShapeId> effectiveAuthSchemes() {
                return List.of();
            }
        };
    }
}
