/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

@Command(name = "coralx", mixinStandardHelpOptions = true, version = "1.0",
        description = "Coral CLI tool")
public class CoralX implements Callable<Integer> {
    private static final String AWS_JSON = "awsjson";
    private static final String RPC_V2_CBOR = "rpcv2-cbor";
    private static final String REST_JSON = "restjson";
    private static final String REST_XML = "restxml";

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

        if (input != null) {
            try (JsonCodec codec = JsonCodec.builder().build()) {
                byte[] jsonBytes = input.getBytes(StandardCharsets.UTF_8);
                var inputDocument = codec.createDeserializer(jsonBytes).readDocument();
                return client.call(operation, inputDocument).asObject();
            }
        }
        if (inputPath != null) {
            try (JsonCodec codec = JsonCodec.builder().build();
                    FileInputStream fis = new FileInputStream(inputPath);
                    InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(isr)) {

                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append(System.lineSeparator());
                }

                var inputDocument = codec.createDeserializer(content.toString().getBytes()).readDocument();
                return client.call(operation, inputDocument).asObject();
            }
        }
        return client.call(operation).asObject();
    }

    private static List<File> getResourceFiles() {
        List<File> resourceFiles = new ArrayList<>();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            String[] knownSmithyFiles = {
                    "aws.api.smithy",
                    "aws.auth.smithy",
                    "aws.customizations.smithy",
                    "aws.protocols.smithy",
            };

            for (String smithyFile : knownSmithyFiles) {
                try (InputStream inputStream = classLoader.getResourceAsStream(smithyFile)) {
                    if (inputStream != null) {
                        File tempFile = File.createTempFile(
                                smithyFile.replace(".smithy", ""),
                                ".smithy"
                        );
                        tempFile.deleteOnExit();
                        Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        resourceFiles.add(tempFile);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("IO exception when accessing resources: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error occurred while fetching resource files: " + e.getMessage());
        }

        return resourceFiles;
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
