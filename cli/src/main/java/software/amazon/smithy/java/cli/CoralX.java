/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Parameters;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
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
            Model model = assembleModel(getFilesFromDirectory(modelPath));
            System.out.println("Available Operations:\n" + model.getOperationShapes());
            return 0;
        } catch (Exception e) {
            logError("Failed to list operations", e);
            return 1;
        }
    }

    private Integer executeOperation() {
        try {
            Model model = assembleModel(getFilesFromDirectory(modelPath));
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

    private Model assembleModel(List<File> modelFiles) {
        var assembler = Model.assembler();

        for (var file : modelFiles) {
            if (file.exists()) {
                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append(System.lineSeparator());
                    }
                    assembler.addUnparsedModel(file.getName(), content.toString());
                } catch (IOException e) {
                    System.err.println("Error reading file: " + file.getPath());
                    e.printStackTrace();
                }
            } else {
                System.err.println("File does not exist: " + file.getPath());
            }
        }

        return assembler.assemble().unwrap();
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
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
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

    private static List<File> getResourceFiles() {
        List<File> resourceFiles = new ArrayList<>(KNOWN_SMITHY_FILES.length);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Arrays.stream(KNOWN_SMITHY_FILES).parallel().forEach(smithyFile -> {
            try (InputStream inputStream = classLoader.getResourceAsStream(smithyFile)) {
                if (inputStream != null) {
                    String baseName = smithyFile.replace(".smithy", "");
                    Path tempFile = Files.createTempFile(baseName, ".smithy");
                    tempFile.toFile().deleteOnExit();

                    try (BufferedInputStream bis = new BufferedInputStream(inputStream)) {
                        Files.copy(bis, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        synchronized(resourceFiles) {
                            resourceFiles.add(tempFile.toFile());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("IO exception when accessing resources: " + e.getMessage());
            }
        });

        return Collections.unmodifiableList(resourceFiles);
    }

    private static List<File> getFilesFromDirectory(String directoryPath) {
        List<File> fileList = new ArrayList<>(getResourceFiles());

        try {
            File directory = new File(directoryPath);

            if (!directory.exists()) {
                System.out.println("Directory does not exist: " + directoryPath);
                return fileList;
            }

            if (!directory.isDirectory()) {
                System.out.println("Path is not a directory: " + directoryPath);
                return fileList;
            }

            File[] files = directory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.add(file);
                    } else if (file.isDirectory()) {
                        fileList.addAll(getFilesFromDirectory(file.getAbsolutePath()));
                    }
                }
            }

        } catch (SecurityException e) {
            System.err.println("Security exception when accessing directory: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error occurred while fetching directory files: " + e.getMessage());
        }

        return fileList;
    }

    private void logError(String message, Exception e) {
        System.err.println(message + ": " + e.getMessage());
    }
}
