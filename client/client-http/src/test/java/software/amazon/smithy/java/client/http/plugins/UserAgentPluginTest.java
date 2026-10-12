/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.FeatureId;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

public class UserAgentPluginTest {

    private static final String KOTLIN_PROPERTY = "aws.userAgentAppId";
    private static final String JAVA_V2_PROPERTY = "sdk.ua.appId";
    private static final String STATIC_PREFIX = "smithy-java/\\S+ ua/2\\.1 os/\\S+ lang/java#\\S+";

    private String savedKotlinProperty;
    private String savedJavaV2Property;

    @BeforeEach
    public void saveProperties() {
        savedKotlinProperty = System.clearProperty(KOTLIN_PROPERTY);
        savedJavaV2Property = System.clearProperty(JAVA_V2_PROPERTY);
    }

    @AfterEach
    public void restoreProperties() {
        restore(KOTLIN_PROPERTY, savedKotlinProperty);
        restore(JAVA_V2_PROPERTY, savedJavaV2Property);
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @Test
    public void addsDefaultAgent() throws Exception {
        var ua = userAgent(Context.create());

        assertThat(ua, matchesPattern(STATIC_PREFIX));
    }

    @Test
    public void addsApplicationId() throws Exception {
        var context = Context.create();
        context.put(ClientContext.APPLICATION_ID, "hello there");

        var ua = userAgent(context);

        assertThat(ua, matchesPattern(STATIC_PREFIX + " app/hello_there"));
    }

    @Test
    public void ignoresEmptyApplicationId() throws Exception {
        var context = Context.create();
        context.put(ClientContext.APPLICATION_ID, "");

        assertThat(userAgent(context), equalTo(userAgent(Context.create())));
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

    private static Set<FeatureId> features() {
        Set<FeatureId> s = new LinkedHashSet<>();
        s.add(Features.FOO);
        s.add(Features.BAR);
        s.add(Features.BAZ);
        return s;
    }

    @Test
    public void addsFeatureIds() throws Exception {
        var context = Context.create();
        context.put(CallContext.FEATURE_IDS, features());

        var ua = userAgent(context);

        assertThat(ua, matchesPattern(STATIC_PREFIX + " m/FOO,BAR,baz_baz"));
    }

    @Test
    public void addsApplicationIdAndFeatureIds() throws Exception {
        var context = Context.create();
        context.put(ClientContext.APPLICATION_ID, "hello there");
        context.put(CallContext.FEATURE_IDS, features());

        var ua = userAgent(context);

        assertThat(ua, matchesPattern(STATIC_PREFIX + " app/hello_there m/FOO,BAR,baz_baz"));
    }

    @Test
    public void defaultsApplicationIdFromSystemPropertyWhenClientIsConfigured() {
        System.setProperty(KOTLIN_PROPERTY, "from-property");

        var config = httpConfigBuilder();
        new UserAgentPlugin().configureClient(config);

        assertThat(config.context().get(ClientContext.APPLICATION_ID), equalTo("from-property"));
    }

    @Test
    public void explicitApplicationIdWinsOverSystemProperty() {
        System.setProperty(KOTLIN_PROPERTY, "from-property");

        var config = httpConfigBuilder().putConfig(ClientContext.APPLICATION_ID, "explicit");
        new UserAgentPlugin().configureClient(config);

        assertThat(config.context().get(ClientContext.APPLICATION_ID), equalTo("explicit"));
    }

    @Test
    public void fallsBackToJavaSdkPropertyName() {
        System.setProperty(JAVA_V2_PROPERTY, "from-sdk-property");

        var config = httpConfigBuilder();
        new UserAgentPlugin().configureClient(config);

        assertThat(config.context().get(ClientContext.APPLICATION_ID), equalTo("from-sdk-property"));
    }

    @Test
    public void prefersKotlinPropertyNameOverJavaSdkPropertyName() {
        System.setProperty(KOTLIN_PROPERTY, "kotlin");
        System.setProperty(JAVA_V2_PROPERTY, "java");

        assertThat(UserAgentPlugin.resolveDefaultAppId(), equalTo("kotlin"));
    }

    @Test
    public void blankPropertyIsTreatedAsUnsetAndStripsWhitespace() {
        System.setProperty(KOTLIN_PROPERTY, "   ");
        System.setProperty(JAVA_V2_PROPERTY, "  padded  ");

        assertThat(UserAgentPlugin.resolveDefaultAppId(), equalTo("padded"));
    }

    @Test
    public void resolvesApplicationIdPerClientNotPerProcess() {
        System.setProperty(KOTLIN_PROPERTY, "first");
        var first = httpConfigBuilder();
        new UserAgentPlugin().configureClient(first);

        System.setProperty(KOTLIN_PROPERTY, "second");
        var second = httpConfigBuilder();
        new UserAgentPlugin().configureClient(second);

        assertThat(first.context().get(ClientContext.APPLICATION_ID), equalTo("first"));
        assertThat(second.context().get(ClientContext.APPLICATION_ID), equalTo("second"));
    }

    @Test
    public void doesNotConfigureNonHttpClients() {
        System.setProperty(KOTLIN_PROPERTY, "from-property");

        var config = ClientConfig.builder();
        new UserAgentPlugin().configureClient(config);

        assertThat(config.context().get(ClientContext.APPLICATION_ID), nullValue());
    }

    private static ClientConfig.Builder httpConfigBuilder() {
        return ClientConfig.builder().transport(new JavaHttpClientTransport());
    }

    private static String userAgent(Context context) throws Exception {
        var interceptor = new UserAgentPlugin.UserAgentInterceptor();
        var req = HttpRequest.create().setUri(new URI("/")).setMethod("GET").toUnmodifiable();
        var updated = interceptor.modifyBeforeTransmit(new RequestHook<>(createOperation(), context, new Foo(), req));
        var values = updated.headers().allValues("user-agent");
        assertThat(values, hasSize(1));
        return values.get(0);
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

    private static ApiOperation<SerializableStruct, SerializableStruct> createOperation() {
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
            public ApiService service() {
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
        };
    }
}
