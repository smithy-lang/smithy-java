/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import picocli.CommandLine.Parameters;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ArgGroup;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.aws.client.core.identity.EnvironmentVariableIdentityResolver;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.aws.client.restxml.RestXmlClientProtocol;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.rpcv2.RpcV2CborProtocol;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

@Command(name = "smithy-call", mixinStandardHelpOptions = true, version = "1.0",
        description = "Smithy Call, send request to Smithy services using Smithy models")
final class SmithyCall implements Callable<Integer> {
    private static final JsonCodec CODEC = JsonCodec.builder().build();
    private static final Logger LOGGER = Logger.getLogger(SmithyCall.class.getName());

    private static final String[] BASE_RESOURCE_FILES = {
            "aws.api.smithy",
            "aws.auth.smithy",
            "aws.customizations.smithy",
            "aws.protocols.smithy"
    };

    @Parameters(index = "0", description = "Service Name (e.g. com.example#CoffeeShop or CoffeeShop)")
    private String service;

    @Parameters(index = "1", description = "Name of the operation to perform on the service", arity = "0..1")
    private String operation;

    @Option(names = "--debug", description = "Enable debug logging")
    private boolean debug;

    @Option(names = { "-m", "--model-path" }, description = "Path to a Smithy model or a directory Smithy models. This argument can be repeated to provide multiple files or directories.", required = true)
    private String[] modelPath;

    @Option(names = "--input-path", description = "Path to a JSON file containing input parameters for the operation")
    private String inputPath;

    @Option(names = "--input-json", description = "JSON string containing input parameters for the operation")
    private String inputJson;

    @Option(names = "--url", description = "Endpoint URL for the service")
    private String url;

    @Option(names = { "-p", "--protocol" }, description = "Supported protocols: ${COMPLETION-CANDIDATES}")
    private ProtocolType protocolType;

    @Option(names = "--list-operations", description = "List all available operations for the specified service")
    private boolean listOperations;

    @ArgGroup(exclusive = false)
    Authentication auth;

    static class Authentication {
        @Option(names = { "-a", "--auth" }, description = "Authentication method to use (e.g., sigv4), smithy.api#noAuth is applied by default", required = true)
        private String authType;

        @Option(names = { "--aws-region" }, description = "AWS region for SigV4 authentication")
        private String awsRegion;

        @Option(names = { "--aws-profile", "--profile"}, description = "AWS profile to use for authentication")
        private String awsProfile;
    }

    @Override
    public Integer call() {
        setupLogger();
        try {
            if (!listOperations && operation == null) {
                throw new IllegalArgumentException("Operation is required when not listing operations");
            }
            return listOperations ? listOperationsForService() : executeOperation();
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error occurred", e);
            return 1;
        }
    }

    private void setupLogger() {
        LogManager.getLogManager().reset();
        LOGGER.setLevel(debug ? Level.FINE : Level.INFO);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(debug ? Level.FINE : Level.INFO);
        LOGGER.addHandler(handler);

        LOGGER.setUseParentHandlers(false);
    }

    private ShapeId getServiceInput(Model model) {
        LOGGER.fine("Checking if the provided model contains service: " + service);

        ShapeId serviceInput = null;
        Set<ServiceShape> serviceShapes = model.getServiceShapes();
        Set<ShapeId> serviceShapeIds = new HashSet<>(serviceShapes.size());
        for (ServiceShape shape : serviceShapes) {
            serviceShapeIds.add(shape.toShapeId());
        }

        if (!service.contains("#")) {
            for (ShapeId serviceShape : serviceShapeIds) {
                if (serviceShape.getName().equals(service)) {
                    if (serviceInput == null) {
                        serviceInput = serviceShape;
                    } else {
                        throw new IllegalArgumentException("Multiple services found with name " + service + " in model.\nAvailable services: " + serviceShapeIds);
                    }
                }
            }
        } else {
            serviceInput = ShapeId.from(service);
        }

        if (serviceInput == null || !serviceShapeIds.contains(serviceInput)) {
            throw new IllegalArgumentException("Service " + service + " not found in model.\nAvailable services: " + serviceShapeIds);
        }

        return serviceInput;
    }

    private Integer listOperationsForService() {
        LOGGER.fine("Listing operations for service: " + service);
        try {
            Model model = assembleModel(modelPath);
            getServiceInput(model);

            Set<OperationShape> operations = model.getOperationShapes();
            StringBuilder sb = new StringBuilder();
            for (OperationShape operation : operations) {
                sb.append(operation.getId().getName()).append("\n");
            }
            if (!sb.isEmpty()) {
                sb.setLength(sb.length() - 1);
            }

            String result = sb.toString();
            System.out.println(result);

            return 0;
        }
        catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());
            return 1;
        }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list operations", e);
            return 1;
        }
    }

    private Integer executeOperation() {
        LOGGER.fine("Executing operation: " + operation);
        try {
            Model model = assembleModel(modelPath);
            ShapeId serviceInput = getServiceInput(model);

            DynamicClient client = buildDynamicClient(model, serviceInput);
            Document result = executeClientCall(client);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ShapeSerializer serializer = CODEC.createSerializer(outputStream)) {
                result.serialize(serializer);
            }
            String output = outputStream.toString(StandardCharsets.UTF_8);
            System.out.println(output);

            return 0;
        }
        catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());
            return 1;
        }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Operation execution failed", e);
            return 1;
        }
    }

    private Model assembleModel(String[] directoryPath) {
        LOGGER.fine("Assembling model from directory path(s): " + Arrays.toString(directoryPath));
        var assembler = Model.assembler()
            // ignore unknown traits because we don't have a way of dynamically loading non-core traits at the moment
            .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true);

        // Add base resource files
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String smithyFile : BASE_RESOURCE_FILES) {
            URL resourceUrl = classLoader.getResource(smithyFile);
            if (resourceUrl != null) {
                assembler.addImport(resourceUrl);
            } else {
                LOGGER.log(Level.SEVERE, "Base resource not found: " + smithyFile, new NoSuchFileException(smithyFile));
            }
        }

        // Add model files
        for (String path : directoryPath) {
            assembler.addImport(path);
        }
        return assembler.assemble().unwrap();
    }

    private DynamicClient buildDynamicClient(Model model, ShapeId serviceInput) {
        LOGGER.fine("Building dynamic client");
        if (url == null) {
            throw new IllegalArgumentException("Service endpoint URL is required. Please provide the --url option.");
        }

        DynamicClient.Builder builder = DynamicClient.builder()
                .service(serviceInput)
                .model(model)
                .endpointResolver(EndpointResolver.staticEndpoint(url));

        Shape shape = model.expectShape(serviceInput);
        configureAuth(builder, serviceInput, shape);
        configureProtocol(builder, serviceInput);

        return builder.build();
    }

    private void configureAuth(DynamicClient.Builder builder, ShapeId serviceInput, Shape shape) {
        if (auth != null) {
            switch (auth.authType.toLowerCase()) {
                case "sigv4", "aws":
                    if (auth.awsRegion == null) {
                        throw new IllegalArgumentException("SigV4 auth requires --aws-region to be set. Please provide the --aws-region option.");
                    }
                    String signingName = shape.getTrait(SigV4Trait.class)
                            .map(SigV4Trait::getName)
                            .orElseGet(() -> shape.getTrait(ServiceTrait.class)
                                    .map(ServiceTrait::getArnNamespace)
                                    .orElse(serviceInput.getNamespace().toLowerCase()));

                    LOGGER.fine("Configuring SigV4 authentication with service signing name detected in model: " + signingName);
                    builder.putConfig(RegionSetting.REGION, auth.awsRegion)
                            .putSupportedAuthSchemes(new SigV4AuthScheme(signingName))
                            .authSchemeResolver(AuthSchemeResolver.DEFAULT);

                    if (auth.awsProfile != null) {
                        var profileBuilder = ProfileCredentialsProvider.builder()
                                .profileName(auth.awsProfile);
                        builder.addIdentityResolver(new SdkCredentialsResolver(profileBuilder.build()));
                    } else {
                        builder.addIdentityResolver(new EnvironmentVariableIdentityResolver());
                    }
                    break;
                case "none":
                    builder.authSchemeResolver(AuthSchemeResolver.NO_AUTH);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported auth type: " + auth.authType);
            }
        } else {
            builder.authSchemeResolver(AuthSchemeResolver.NO_AUTH);
        }
    }

    private void configureProtocol(DynamicClient.Builder builder, ShapeId serviceInput) {
        if (protocolType != null) {
            builder.protocol(switch (protocolType) {
                case AWS_JSON -> new AwsJson1Protocol(serviceInput);
                case RPC_V2_CBOR -> new RpcV2CborProtocol(serviceInput);
                case REST_JSON -> new RestJsonClientProtocol(serviceInput);
                case REST_XML -> new RestXmlClientProtocol(serviceInput);
            });
        }
    }

    private Document executeClientCall(DynamicClient client) throws Exception {
        if (inputJson != null && inputPath != null) {
            throw new IllegalArgumentException("Cannot specify both '--input-json' and '--input-path'. Please provide only one.");
        }

        LOGGER.fine("Executing client call");

        if (inputJson == null && inputPath == null) {
            return client.call(operation);
        }

        byte[] inputBytes;
        if (inputJson != null) {
            inputBytes = inputJson.getBytes(StandardCharsets.UTF_8);
        } else {
            inputBytes = Files.readAllBytes(Path.of(inputPath));
        }

        Document input;
        try (var deserializer = CODEC.createDeserializer(inputBytes)) {
            input = deserializer.readDocument();
        }
        return client.call(operation, input);
    }
}
