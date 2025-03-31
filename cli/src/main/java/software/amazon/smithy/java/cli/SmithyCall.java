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
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.aws.client.core.identity.EnvironmentVariableIdentityResolver;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.aws.client.restxml.RestXmlClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.protocols.rpcv2.RpcV2CborProtocol;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Command(name = "smithy-call", mixinStandardHelpOptions = true, version = "1.0",
        description = "Smithy Java CLI")
public final class SmithyCall implements Callable<Integer> {
    private static final JsonCodec CODEC = JsonCodec.builder().build();
    private static final Logger LOGGER = Logger.getLogger(SmithyCall.class.getName());

    private static final String[] BASE_RESOURCE_FILES = {
            "aws.api.smithy",
            "aws.auth.smithy",
            "aws.customizations.smithy",
            "aws.protocols.smithy"
    };

    @Parameters(index = "0", description = "Service Name")
    private String service;

    @Parameters(index = "1", description = "Name of the operation to perform on the service", arity = "0..1")
    private String operation;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose logging")
    private boolean verbose;

    @Option(names = { "-m", "--model-path" }, description = "Path to a directory containing all necessary .smithy service model files", required = true)
    private String[] modelPath;

    @Option(names = "--input-path", description = "Path to a JSON file containing input parameters for the operation")
    private String inputPath;

    @Option(names = "--input-json", description = "JSON string containing input parameters for the operation")
    private String input;

    @Option(names = "--url", description = "Endpoint URL for the service")
    private String url;

    @Option(names = { "-p", "--protocol" }, description = "Communication protocol to use (options: awsjson, rpcv2-cbor, restjson, and restxml)")
    private String protocol;

    @Option(names = "--list-operations", description = "List all available operations for the specified service")
    private boolean listOperations;

    @ArgGroup(exclusive = false)
    Authentication auth;

    static class Authentication {
        @Option(names = { "-a", "--auth" }, description = "Authentication method to use (e.g., sigv4), smithy.api#noAuth is applied by default", required = true)
        private String authType;

        @Option(names = { "--aws-region" }, description = "AWS region for SigV4 authentication")
        private String awsRegion;
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
            LOGGER.log(Level.SEVERE, "Invalid input", e);
            return 1;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error occurred", e);
            return 1;
        }
    }

    private void setupLogger() {
        LogManager.getLogManager().reset();
        LOGGER.setLevel(verbose ? Level.FINE : Level.INFO);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(verbose ? Level.FINE : Level.INFO);
        LOGGER.addHandler(handler);

        LOGGER.setUseParentHandlers(false);
    }

    private Integer listOperationsForService() {
        LOGGER.fine("Listing operations for service: " + service);
        try {
            Model model = assembleModel(modelPath);

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
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list operations", e);
            return 1;
        }
    }

    private Integer executeOperation() {
        LOGGER.fine("Executing operation: " + operation);
        try {
            Model model = assembleModel(modelPath);
            ShapeId serviceInput = validateServiceExists(model);

            DynamicClient client = buildDynamicClient(model, serviceInput);
            Document result = executeClientCall(client);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ShapeSerializer serializer = CODEC.createSerializer(outputStream)) {
                result.serialize(serializer);
            }
            String output = outputStream.toString(StandardCharsets.UTF_8);
            System.out.println(output);

            return 0;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Operation execution failed", e);
            return 1;
        }
    }

    private Model assembleModel(String[] directoryPath) {
        LOGGER.fine("Assembling model from directory path(s): " + Arrays.toString(directoryPath));
        var assembler = Model.assembler();

        // Add base resource files
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String smithyFile : BASE_RESOURCE_FILES) {
            URL resourceUrl = classLoader.getResource(smithyFile);
            if (resourceUrl != null) {
                assembler.addImport(resourceUrl);
            } else {
                LOGGER.log(Level.SEVERE, "Resource not found: " + smithyFile, new NoSuchFileException(smithyFile));
            }
        }

        // Add model files
        for (String path : directoryPath) {
            assembler.addImport(path);
        }
        return assembler.assemble().unwrap();
    }

    private ShapeId validateServiceExists(Model model) {
        LOGGER.fine("Checking if the provided model contains service: " + service);
        ShapeId serviceInput = ShapeId.from(service);
        if (!model.getShapeIds().contains(serviceInput)) {
            throw new IllegalArgumentException("Service " + service + " not found in model");
        }
        return serviceInput;
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

        configureAuth(builder, serviceInput);
        configureProtocol(builder, serviceInput);
        configureInputInterceptor(builder);

        return builder.build();
    }

    private void configureAuth(DynamicClient.Builder builder, ShapeId serviceInput) {
        String defaultArnNamespace = serviceInput.getNamespace().toLowerCase();
        if (auth != null) {
            switch (auth.authType.toLowerCase()) {
                case "sigv4":
                case "aws":
                    if (auth.awsRegion == null) {
                        throw new IllegalArgumentException("SigV4 auth requires --aws-region to be set. Please provide the --aws-region option.");
                    }
                    builder.putConfig(RegionSetting.REGION, auth.awsRegion)
                            .putSupportedAuthSchemes(new SigV4AuthScheme(defaultArnNamespace))
                            .authSchemeResolver(AuthSchemeResolver.DEFAULT)
                            .addIdentityResolver(new EnvironmentVariableIdentityResolver());
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
        if (protocol != null) {
            ProtocolType protocolType = ProtocolType.fromString(protocol);
            switch (protocolType) {
                case AWS_JSON:
                    builder.protocol(new AwsJson1Protocol(serviceInput));
                    break;
                case RPC_V2_CBOR:
                    builder.protocol(new RpcV2CborProtocol(serviceInput));
                    break;
                case REST_JSON:
                    builder.protocol(new RestJsonClientProtocol(serviceInput));
                    break;
                case REST_XML:
                    builder.protocol(new RestXmlClientProtocol(serviceInput));
                    break;
            }
        }
    }

    private void configureInputInterceptor(DynamicClient.Builder builder) {
        if (input != null || inputPath != null) {
            builder.addInterceptor(new ClientInterceptor() {
                @Override
                public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {}
            });
        }
    }

    private Document executeClientCall(DynamicClient client) throws Exception {
        if (input != null && inputPath != null) {
            throw new IllegalArgumentException("Cannot specify both '--input-json' and '--input-path'. Please provide only one.");
        }

        LOGGER.fine("Executing client call");

        if (input == null && inputPath == null) {
            return client.call(operation);
        }

        Document inputDocument;
        if (input != null) {
            inputDocument = CODEC.createDeserializer(input.getBytes(StandardCharsets.UTF_8)).readDocument();
        } else {
            String content = Files.readString(Path.of(inputPath), StandardCharsets.UTF_8);
            inputDocument = CODEC.createDeserializer(content.getBytes(StandardCharsets.UTF_8)).readDocument();
        }

        return client.call(operation, inputDocument);
    }
}
