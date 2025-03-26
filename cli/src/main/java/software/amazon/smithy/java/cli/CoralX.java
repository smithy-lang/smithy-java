/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Parameters;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
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
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.client.protocols.rpcv2.RpcV2CborProtocol;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

@Command(name = "coralx", mixinStandardHelpOptions = true, version = "1.0",
        description = "Coral CLI tool")
public class CoralX implements Callable<Integer> {
    private static final JsonCodec CODEC = JsonCodec.builder().build();

    private static final String AWS_JSON = "awsjson";
    private static final String RPC_V2_CBOR = "rpcv2-cbor";
    private static final String REST_JSON = "restjson";
    private static final String REST_XML = "restxml";

    private static final String[] KNOWN_SMITHY_FILES = {
            "aws.api.smithy",
            "aws.auth.smithy",
            "aws.customizations.smithy",
            "aws.protocols.smithy"
    };

    @Parameters(index = "0", description = "Service Name")
    private String service;

    @Parameters(index = "1", description = "Operation Name", arity = "0..1")
    private String operation;

    @Option(names = { "-m", "--model-path" }, description = "Model file path", required = true)
    private String modelPath;

    @Option(names = "--input-path", description = "Input json file path")
    private String inputPath;

    @Option(names = "--input-json", description = "Input JSON string")
    private String input;

    @Option(names = "--url", description = "Service URL")
    private String url;

    @Option(names = { "-p", "--protocol" }, description = "Optionally specified protocol")
    private String protocol;

    @Option(names = "--list-operations", description = "List operations for the specified service")
    private boolean listOperations;

    @Override
    public Integer call() {
        try {
            validateInput();
            return listOperations ? listOperationsForService() : executeOperation();
        } catch (Exception e) {
            logError("Command execution failed", e);
            return 1;
        }
    }

    private void validateInput() {
        if (!listOperations && operation == null) {
            throw new IllegalArgumentException("Operation required");
        }
    }

    private Integer listOperationsForService() {
        try {
            Model model = assembleModel(modelPath);
            System.out.println("Available Operations:\n" + model.getOperationShapes());
            return 0;
        } catch (Exception e) {
            logError("Failed to list operations", e);
            return 1;
        }
    }

    private Integer executeOperation() {
        try {
            Model model = assembleModel(modelPath);
            ShapeId serviceInput = validateServiceExists(model);

            DynamicClient client = buildDynamicClient(model, serviceInput);
            Object result = executeClientCall(client);

            System.out.println("Results:\n" + result);

            return 0;
        } catch (Exception e) {
            logError("Operation execution failed", e);
            return 1;
        }
    }

    private Model assembleModel(String directoryPath) {
        var assembler = Model.assembler();

        // Add resource files
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        for (String smithyFile : KNOWN_SMITHY_FILES) {
            URL resourceUrl = classLoader.getResource(smithyFile);
            if (resourceUrl != null) {
                assembler.addImport(resourceUrl);
            } else {
                System.err.println("Resource not found: " + smithyFile);
            }
        }

        // Add model files
        try {
            assembler.addImport(directoryPath);
            return assembler.assemble().unwrap();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to assemble model from directory: " + e, e);
        }
    }

    private ShapeId validateServiceExists(Model model) {
        ShapeId serviceInput = ShapeId.from(service);
        if (!model.getShapeIds().contains(serviceInput)) {
            throw new IllegalArgumentException("Service " + service + " not found in model");
        }
        return serviceInput;
    }

    private DynamicClient buildDynamicClient(Model model, ShapeId serviceInput) {
        if (url == null) {
            throw new IllegalArgumentException("Service endpoint URL required");
        }

        DynamicClient.Builder builder = DynamicClient.builder()
                .service(serviceInput)
                .model(model)
                .putConfigIfAbsent(RegionSetting.REGION, "us-east-1") // this will probably be an input
                .putSupportedAuthSchemes(new SigV4AuthScheme("bt111fluuperm")) // can maybe assume that this would just be service name lowercase?
                .authSchemeResolver(AuthSchemeResolver.DEFAULT)
                .addIdentityResolver(new EnvironmentVariableIdentityResolver())
                .transport(new JavaHttpClientTransport())
                .endpointResolver(EndpointResolver.staticEndpoint(url));

        configureProtocol(builder, serviceInput);
        configureInputInterceptor(builder);

        return builder.build();
    }

    private void configureProtocol(DynamicClient.Builder builder, ShapeId serviceInput) {
        if (protocol != null) {
            switch (protocol.toLowerCase()) {
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
                default:
                    throw new IllegalArgumentException("Unsupported protocol type: " + protocol);
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

    private Object executeClientCall(DynamicClient client) throws Exception {
        if (input != null && inputPath != null) {
            throw new IllegalArgumentException("Cannot specify both '--input-json' and '--input-path'. Please provide only one.");
        }

        if (input == null && inputPath == null) {
            return client.call(operation).asObject();
        }

        Document inputDocument;
        if (input != null) {
            inputDocument = CODEC.createDeserializer(input.getBytes(StandardCharsets.UTF_8)).readDocument();
        } else {
            String content = Files.readString(Path.of(inputPath), StandardCharsets.UTF_8);
            inputDocument = CODEC.createDeserializer(content.getBytes(StandardCharsets.UTF_8)).readDocument();
        }

        return client.call(operation, inputDocument).asObject();
    }

    private void logError(String message, Exception e) {
        System.err.println(message + ": " + e.getMessage());
    }
}
