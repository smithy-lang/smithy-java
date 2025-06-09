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
import java.util.Set;
import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.awsmcp.model.PreRequest;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ModelSerializer;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.modelbundle.api.ModelBundler;
import software.amazon.smithy.modelbundle.api.model.AdditionalInput;
import software.amazon.smithy.modelbundle.api.model.SmithyBundle;

public final class AwsServiceBundler extends ModelBundler {
    private static final ShapeId ENDPOINT_TESTS = ShapeId.from("smithy.rules#endpointTests");

    // visible for testing
    static final Map<String, String> GH_URIS_BY_SERVICE = new HashMap<>();

    static {
        // line format: Title (which may contain spaces)|sdk-id/service/YYYY/MM/DD/sdk-id-YYYY-MM-DD.json
        // sdk title, pipe character, github url to download the model (of which the first component is the sdk id)
        try (var models = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(AwsServiceBundler.class.getResourceAsStream("/models.txt")),
                StandardCharsets.UTF_8))) {
            models.lines()
                    .forEach(line -> {
                        var start = line.indexOf("|") + 1;
                        var end = line.indexOf("/", start);
                        GH_URIS_BY_SERVICE.put(line.substring(start, end).toLowerCase(Locale.ROOT),
                                line.substring(start));
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final ModelResolver resolver;
    private final String serviceName;
    private final Set<String> exposedOperations;
    private final Set<String> blockedOperations;
    private final Set<String> allowedPrefixes;
    private final Set<String> blockedPrefixes;

    private AwsServiceBundler(Builder builder) {
        this.serviceName = builder.serviceName;
        this.resolver = builder.resolver;
        this.exposedOperations = builder.exposedOperations;
        this.blockedOperations = builder.blockedOperations;
        this.allowedPrefixes = builder.allowedPrefixes;
        this.blockedPrefixes = builder.blockedPrefixes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceName;
        private ModelResolver resolver = GithubModelResolver.INSTANCE;
        private Set<String> exposedOperations = Collections.emptySet();
        private Set<String> blockedOperations = Collections.emptySet();
        private Set<String> allowedPrefixes = Collections.emptySet();
        private Set<String> blockedPrefixes = Collections.emptySet();

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        Builder resolver(ModelResolver resolver) {
            this.resolver = resolver;
            return this;
        }

        public Builder exposedOperations(Set<String> exposedOperations) {
            this.exposedOperations = exposedOperations;
            return this;
        }

        public Builder blockedOperations(Set<String> blockedOperations) {
            this.blockedOperations = blockedOperations;
            return this;
        }

        public Builder readOnlyOperations() {
            this.allowedPrefixes(ApiStandardTerminology.READ_ONLY_API_PREFIXES);
            this.blockedPrefixes(ApiStandardTerminology.WRITE_API_PREFIXES);
            return this;
        }

        public Builder allowedPrefixes(Set<String> allowedPrefixes) {
            this.allowedPrefixes = allowedPrefixes;
            return this;
        }

        public Builder blockedPrefixes(Set<String> blockedPrefixes) {
            this.blockedPrefixes = blockedPrefixes;
            return this;
        }

        public AwsServiceBundler build() {
            return new AwsServiceBundler(this);
        }
    }

    private static ServiceShape findService(Model model, String name) {
        for (var service : model.getServiceShapes()) {
            if (service.hasTrait(ServiceTrait.ID) && service.expectTrait(ServiceTrait.class)
                    .getSdkId() != null) {
                return service;
            }
        }
        throw new RuntimeException("couldn't find service with name " + name);
    }

    @Override
    public SmithyBundle bundle() {
        try {
            var modelString = resolver.getModel(serviceName);
            var model = new ModelAssembler()
                    .addUnparsedModel(serviceName + ".json", modelString)
                    .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                    .discoverModels()
                    .assemble()
                    .unwrap();
            var bundle = AwsServiceMetadata.builder();
            var service = findService(model, serviceName);
            bundle.serviceName(service.getId().getName())
                    .endpoints(Collections.emptyMap());
            // guaranteed to exist
            var sigv4Trait = service.expectTrait(SigV4Trait.class);
            var signingName = sigv4Trait.getName();
            bundle.sigv4SigningName(signingName);
            service.getTrait(EndpointTrait.class);
            for (var trait : service.getAllTraits().values()) {
                var id = trait.toShapeId();
                if (id.equals(ENDPOINT_TESTS)) {
                    bundle.endpoints(parseEndpoints(trait.toNode().expectObjectNode()));
                }
            }
            return SmithyBundle.builder()
                    .config(Document.of(bundle.build()))
                    .configType("aws")
                    .serviceName(service.getId().toString())
                    .model(serializeModel(cleanAndFilterModel(model,
                            service,
                            exposedOperations,
                            blockedOperations,
                            allowedPrefixes,
                            blockedPrefixes)))
                    .additionalInput(AdditionalInput.builder()
                            .identifier(PreRequest.$ID.toString())
                            .model(loadModel("/META-INF/smithy/bundle.smithy"))
                            .build())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to bundle " + serviceName, e);
        }
    }

    private static String serializeModel(software.amazon.smithy.model.Model model) {
        return ObjectNode.printJson(ModelSerializer.builder()
                .build()
                .serialize(model));
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
                    .uri(URI.create("https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/"
                            + Objects.requireNonNull(GH_URIS_BY_SERVICE.get(serviceName),
                                    "No known service name " + serviceName)))
                    .build(), HttpResponse.BodyHandlers.ofString())
                    .body();
        }
    }
}
