/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.utils.IoUtils;

public class EndpointRulesPluginTest {
    @Test
    public void addsEndpointResolver() {
        var contents = IoUtils.readUtf8Resource(getClass(), "example-complex-ruleset.json");
        var program = new RulesEngine().compile(EndpointRuleSet.fromNode(Node.parse(contents)));
        var plugin = EndpointRulesPlugin.from(program);
        var builder = ClientConfig.builder();
        plugin.configureClient(builder);

        assertThat(builder.endpointResolver(), instanceOf(EndpointRulesResolver.class));
    }

    @Test
    public void doesNotModifyExistingResolver() {
        var contents = IoUtils.readUtf8Resource(getClass(), "example-complex-ruleset.json");
        var program = new RulesEngine().compile(EndpointRuleSet.fromNode(Node.parse(contents)));
        var plugin = EndpointRulesPlugin.from(program);
        var builder = ClientConfig.builder().endpointResolver(EndpointResolver.staticHost("foo.com"));
        plugin.configureClient(builder);

        assertThat(builder.endpointResolver(), not(instanceOf(EndpointRulesResolver.class)));
    }

    @Test
    public void modifiesResolverIfCustomEndpointSet() {
        var contents = IoUtils.readUtf8Resource(getClass(), "example-complex-ruleset.json");
        var program = new RulesEngine().compile(EndpointRuleSet.fromNode(Node.parse(contents)));
        var plugin = EndpointRulesPlugin.from(program);
        var builder = ClientConfig.builder()
                .endpointResolver(EndpointResolver.staticHost("foo.com"))
                .putConfig(ClientContext.CUSTOM_ENDPOINT, Endpoint.builder().uri("https://example.com").build());
        plugin.configureClient(builder);

        assertThat(builder.endpointResolver(), instanceOf(EndpointRulesResolver.class));
    }

    @Test
    public void onlyModifiesResolverIfProgramFound() {
        var plugin = EndpointRulesPlugin.from((RulesProgram) null);
        var builder = ClientConfig.builder();
        plugin.configureClient(builder);

        assertThat(builder.endpointResolver(), not(instanceOf(EndpointRulesResolver.class)));
    }

    @Test
    public void loadsRulesFromServiceSchemaTraits() {
        var model = Model.assembler()
                .addImport(getClass().getResource("minimal-ruleset.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();

        // Create a service schema.
        var service = model.expectShape(ShapeId.from("example#FizzBuzz"), ServiceShape.class);
        var traits = new Trait[service.getAllTraits().size()];
        int i = 0;
        for (var t : service.getAllTraits().values()) {
            traits[i++] = t;
        }
        var schema = Schema.createService(service.getId(), traits);
        ApiService api = () -> schema;

        // Create the plugin from the service schema.
        var plugin = EndpointRulesPlugin.create();
        var builder = ClientConfig.builder().service(api);
        builder.applyPlugin(plugin);

        assertThat(builder.endpointResolver(), instanceOf(EndpointRulesResolver.class));
    }
}
