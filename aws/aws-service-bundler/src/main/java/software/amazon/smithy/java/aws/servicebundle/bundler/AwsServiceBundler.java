/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.bundler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.awsmcp.model.PreRequest;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ModelSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.modelbundle.api.Bundler;
import software.amazon.smithy.modelbundle.api.model.Bundle;
import software.amazon.smithy.modelbundle.api.model.Model;

final class AwsServiceBundler implements Bundler {
    private static final ShapeId ENDPOINT_TESTS = ShapeId.from("smithy.rules#endpointTests");

    // visible for testing
    static final Map<String, String> GH_URIS_BY_SERVICE = new HashMap<>();
    static {
        // line is in the form fooService/service/version/fooService.json
        try (var models = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(AwsServiceBundler.class.getResourceAsStream("/models.txt")),
                StandardCharsets.UTF_8))) {
            models.lines()
                    .forEach(line -> GH_URIS_BY_SERVICE
                            .put(line.substring(0, line.indexOf("/")).toLowerCase(Locale.ROOT), line));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final ModelResolver resolver;
    private final String serviceName;

    AwsServiceBundler(String... args) {
        this(args[0].toLowerCase(Locale.ROOT), GithubModelResolver.INSTANCE);
    }

    AwsServiceBundler(String serviceName, ModelResolver resolver) {
        this.serviceName = serviceName;
        this.resolver = resolver;
    }

    private static software.amazon.smithy.model.Model adapt(software.amazon.smithy.model.Model model) {
        var template = model.expectShape(PreRequest.$ID).asStructureShape().get();
        var b = model.toBuilder();

        // mix in the PreRequest structure members
        for (var op : model.getOperationShapes()) {
            var input = model.expectShape(op.getInput().get(), StructureShape.class).toBuilder();
            for (var member : template.members()) {
                input.addMember(member.toBuilder()
                        .id(ShapeId.from(input.getId().toString() + "$" + member.getMemberName()))
                        .build());
            }
            b.addShape(input.build());
        }

        for (var service : model.getServiceShapes()) {
            b.addShape(service.toBuilder()
                    // trim the endpoint rules because they're huge and we don't need them
                    .removeTrait(ShapeId.from("smithy.rules#endpointRuleSet"))
                    .removeTrait(ENDPOINT_TESTS)
                    .build());
        }

        return b.build();
    }

    @Override
    public Bundle bundle() {
        try {
            var modelString = resolver.getModel(serviceName);
            var model = new ModelAssembler()
                    .addUnparsedModel(serviceName + ".json", modelString)
                    .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                    .discoverModels()
                    .assemble()
                    .unwrap();
            var bundle = AwsServiceMetadata.builder();
            var service = model.getServiceShapes().iterator().next();
            bundle.serviceName(service.getId().getName())
                    .endpoints(Collections.emptyMap());
            var sigv4Trait = service.getTrait(SigV4Trait.class);
            if (sigv4Trait.isEmpty()) {
                throw new RuntimeException("Service " + serviceName + " does not have a SigV4 trait");
            }

            var signingName = sigv4Trait.get().getName();
            bundle.sigv4SigningName(signingName);
            service.getTrait(EndpointTrait.class);
            for (var trait : service.getAllTraits().values()) {
                var id = trait.toShapeId();
                if (id.equals(ENDPOINT_TESTS)) {
                    bundle.endpoints(parseEndpoints(trait.toNode().expectObjectNode()));
                }
            }
            return Bundle.builder()
                    .config(Document.of(bundle.build()))
                    .configType("aws")
                    .serviceName(model.getServiceShapes().iterator().next().getId().toString())
                    .model(Model.builder()
                            .smithyModel(
                                    ObjectNode.printJson(ModelSerializer.builder().build().serialize(adapt(model))))
                            .build())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to bundle " + serviceName, e);
        }
    }

    Map<String, String> parseEndpoints(ObjectNode endpointTests) {
        var testCases = endpointTests.expectArrayMember("testCases");
        var endpoints = new HashMap<String, String>();
        for (var node : testCases) {
            var on = node.expectObjectNode();
            var expect = on.expectObjectMember("expect");
            if (expect.getMember("error").isPresent()) {
                continue;
            }

            var params = on.expectObjectMember("params");
            if (hasFlag(params, "UseFIPS")) {
                continue;
            }

            if (hasFlag(params, "UseDualStack")) {
                continue;
            }

            // kinesis control
            if (hasValue(params, "OperationType")) {
                continue;
            }

            // ddb and s3 aps
            if (hasValue(params, "AccountId") || hasValue(params, "AccountIdEndpointMode")
                    || hasValue(params, "ResourceArn")
                    || hasValue(params, "Endpoint")) {
                continue;
            }

            // s3 bucket APs – the client will handle this, we just need the base url
            if (hasFlag(params, "RequiresAccountId") || hasValue(params, "Bucket")
                    || hasFlag(params, "UseObjectLambdaEndpoint")) {
                continue;
            }

            var region = params.getStringMember("Region")
                    .orElseGet(() -> Node.from("us-east-1"))
                    .getValue();
            var endpoint = expect.expectObjectMember("endpoint")
                    .expectStringMember("url")
                    .getValue();

            if (endpoint.contains("s3-outposts")) {
                // ignore s3 outposts
                continue;
            }

            if (endpoint.startsWith("https://") && endpoint.endsWith(region + ".amazonaws.com")) {
                if (endpoint.endsWith(region + ".amazonaws.com") || endpoint.contains(region + ".amazonaws.com.")) {
                    var prev = endpoints.put(region, endpoint);
                    if (prev != null && !endpoint.equals(prev)) {
                        throw new RuntimeException(
                                "Duplicate endpoint for region " + region + ": " + prev + " and " + endpoint);
                    }
                }
            }
        }
        return endpoints;
    }

    private static boolean hasFlag(ObjectNode params, String key) {
        return params.getBooleanMember(key)
                .map(BooleanNode::getValue)
                .orElse(false);
    }

    private static boolean hasValue(ObjectNode params, String key) {
        return params.getMember(key).isPresent();
    }

    interface ModelResolver {
        String getModel(String serviceName) throws Exception;
    }

    private static final class GithubModelResolver implements ModelResolver {
        private static final HttpClient CLIENT = HttpClient.newHttpClient();
        private static final GithubModelResolver INSTANCE = new GithubModelResolver();

        @Override
        public String getModel(String serviceName) throws Exception {
            return CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/"
                            + Objects.requireNonNull(GH_URIS_BY_SERVICE.get(serviceName),
                                    "No known service name " + serviceName)))
                    .build(), HttpResponse.BodyHandlers.ofString())
                    .body();
        }
    }
}
