/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

class SimpleAuthDetectionPluginTest {

    @Test
    void registersAuthSchemeFactoriesForServiceAuthTraits() {
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        use aws.auth#sigv4
                        use aws.protocols#awsJson1_0

                        @awsJson1_0
                        @sigv4(name: "testservice")
                        service AuthService {
                            operations: [DoThing]
                        }

                        operation DoThing {
                            input := {}
                            output := {}
                        }
                        """)
                .discoverModels()
                .assemble()
                .unwrap();

        var client = DynamicClient.builder()
                .serviceId(ShapeId.from("smithy.example#AuthService"))
                .model(model)
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .build();

        var authSchemes = client.config().supportedAuthSchemes();
        var hasSigV4 = authSchemes.stream().anyMatch(s -> s.schemeId().equals(SigV4Trait.ID));
        assertThat("Expected SigV4 auth scheme to be registered", hasSigV4, is(true));
        assertThat(client.config().authSchemeResolver(), is(AuthSchemeResolver.DEFAULT));
    }

    @Test
    void setsNoAuthResolverWhenNoAuthTraits() {
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        use aws.protocols#awsJson1_0

                        @awsJson1_0
                        service NoAuthService {
                            operations: [DoThing]
                        }

                        operation DoThing {
                            input := {}
                            output := {}
                        }
                        """)
                .discoverModels()
                .assemble()
                .unwrap();

        var client = DynamicClient.builder()
                .serviceId(ShapeId.from("smithy.example#NoAuthService"))
                .model(model)
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .build();

        assertThat(client.config().authSchemeResolver(), equalTo(AuthSchemeResolver.NO_AUTH));
    }
}
