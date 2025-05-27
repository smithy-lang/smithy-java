package software.amazon.smithy.java.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SmithyCallTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private static HttpServer MOCK_SERVER;
    private static int PORT;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private CommandLine commandLine;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpServer() throws IOException {
        // Get a dynamic port by binding to port 0
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        MOCK_SERVER = HttpServer.create(address, 0);
        MOCK_SERVER.setExecutor(Executors.newFixedThreadPool(1));

        MOCK_SERVER.createContext("/", exchange -> {
            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            String target = exchange.getRequestHeaders().getFirst("X-Amz-Target");
            String response;

            if (target != null && target.contains("CreateSprocket")) {
                response = "{\"id\": \"sprocket-123\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/x-amz-json-1.0");
                exchange.sendResponseHeaders(200, response.length());
            } else if (target != null && target.contains("GetSprocket")) {
                if (requestBody.contains("\"id\":\"sprocket-123\"")) {
                    response = "{\"id\": \"sprocket-123\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/x-amz-json-1.0");
                    exchange.sendResponseHeaders(200, response.length());
                } else {
                    response = "{\"__type\":\"InvalidSprocketId\",\"id\":\"invalid-id\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/x-amz-json-1.0");
                    exchange.sendResponseHeaders(400, response.length());
                }
            } else {
                response = "{\"__type\":\"UnknownOperationException\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/x-amz-json-1.0");
                exchange.sendResponseHeaders(400, response.length());
            }

            try (var os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        MOCK_SERVER.start();
        PORT = MOCK_SERVER.getAddress().getPort();
    }

    @AfterAll
    static void tearDownServer() {
        if (MOCK_SERVER != null) {
            MOCK_SERVER.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        commandLine = new CommandLine(new SmithyCall())
            .setCaseInsensitiveEnumValuesAllowed(true);
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testListOperations() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "--list-operations",
                "--model-path", modelDir.toString()
        };

        int exitCode = commandLine.execute(args);
        assertEquals(0, exitCode);
        String output = outContent.toString().trim();
        assertTrue(output.contains("CreateSprocket"));
        assertTrue(output.contains("GetSprocket"));
    }

    @Test
    void testListOperationsWithUnknownService() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#UnknownService",
                "--list-operations",
                "--model-path", modelDir.toString()
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("Service smithy.example#UnknownService not found in model"));
    }

    @Test
    void testUnknownRelativeService() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "UnknownService",
                "--list-operations",
                "--model-path", modelDir.toString()
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("Service UnknownService not found in model"));
    }

    @Test
    void testCreateSprocket() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "CreateSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--protocol", "aws_json",
                "--input-json", "{}"
        };

        int exitCode = commandLine.execute(args);
        assertEquals(0, exitCode);
        String output = outContent.toString().trim();
        assertTrue(output.contains("sprocket-123"));
    }

    @Test
    void testGetSprocket() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "GetSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--input-json", "{\"id\":\"sprocket-123\"}"
        };

        int exitCode = commandLine.execute(args);
        assertEquals(0, exitCode);
        String output = outContent.toString().trim();
        assertTrue(output.contains("sprocket-123"));
    }

    @Test
    void testRelativeShapeId() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "Sprockets",
                "GetSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--input-json", "{\"id\":\"sprocket-123\"}"
        };

        int exitCode = commandLine.execute(args);
        assertEquals(0, exitCode);
        String output = outContent.toString().trim();
        assertTrue(output.contains("sprocket-123"));
    }

    @Test
    void testGetSprocketInvalidId() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "GetSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--input-json", "{\"id\":\"invalid-id\"}"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("InvalidSprocketId"));
    }

    @Test
    void testUnsupportedProtocol() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "GetSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--protocol", "foo",
                "--input-json", "{\"id\":\"sprocket-123\"}"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("Invalid value for option '--protocol'"));
    }

    @Test
    void testNoURL() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "GetSprocket",
                "--model-path", modelDir.toString(),
                "--input-json", "{\"id\":\"sprocket-123\"}"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("Service endpoint URL is required"));
    }

    @Test
    void testDuplicateInput() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "GetSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--input-json", "{\"id\":\"sprocket-123\"}",
                "--input-path", "/some/path"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("Cannot specify both '--input-json' and '--input-path'. Please provide only one."));
    }

    @Test
    void testUnknownOperation() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "UnknownOperation",
                "--model-path", modelDir.toString(),
                "--url", "XXXXXXXXXXXXXXXX:" + PORT,
                "--input-json", "{}"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("Operation 'UnknownOperation' not found in service 'smithy.example#Sprockets'"));
    }

    @Test
    void testWithUnsupportedAuth() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "CreateSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--auth", "foo",
                "--protocol", "aws_json",
                "--input-json", "{}"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("Unsupported auth type: foo"));
    }

    @Test
    void testWithSigV4() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "CreateSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--auth", "sigv4",
                "--aws-region", "us-west-2",
                "--protocol", "aws_json",
                "--input-json", "{}"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("No auth scheme could be resolved for operation"));
    }

    @Test
    void testWithNoAWSRegion() {
        Path modelDir = createSprocketsModelFile();
        String[] args = {
                "smithy.example#Sprockets",
                "CreateSprocket",
                "--model-path", modelDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--auth", "sigv4",
                "--protocol", "aws_json",
                "--input-json", "{}"
        };

        int exitCode = commandLine.execute(args);
        assertTrue(exitCode != 0);
        String error = errContent.toString();
        assertTrue(error.contains("SigV4 auth requires --aws-region to be set."));
    }

    @Test
    void testWithUnknownTraits() {
        Path modelFile = tempDir.resolve("sprockets-unknown-traits.smithy");
        String modelContent = """
        $version: "2"
        namespace smithy.example

        @aws.protocols#awsJson1_0
        @unknownTrait
        service SprocketsUnknown {
            operations: [CreateSprocket]
        }

        @anotherUnknownTrait("some value")
        operation CreateSprocket {
            input := {}
            output := {
                @fieldUnknownTrait
                id: String
            }
        }
        """;
        try {
            Files.write(modelFile, modelContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            fail("Failed to write model file: " + e.getMessage());
        }

        String[] args = {
                "SprocketsUnknown",
                "CreateSprocket",
                "--model-path", tempDir.toString(),
                "--url", "http://localhost:" + PORT,
                "--protocol", "aws_json",
                "--input-json", "{}"
        };

        int exitCode = commandLine.execute(args);

        assertEquals(0, exitCode);
        String output = outContent.toString().trim();
        assertTrue(output.contains("sprocket-123"));
    }

    private Path createSprocketsModelFile() {
        Path modelFile = tempDir.resolve("sprockets.smithy");
        String modelContent = """
            $version: "2"
            namespace smithy.example

            @aws.protocols#awsJson1_0
            service Sprockets {
                operations: [CreateSprocket, GetSprocket]
                errors: [ServiceFooError]
            }

            operation CreateSprocket {
                input := {}
                output := {
                    id: String
                }
                errors: [InvalidSprocketId]
            }

            operation GetSprocket {
                input := {
                    id: String
                }
                output := {
                    id: String
                }
                errors: [InvalidSprocketId]
            }

            @error("client")
            structure InvalidSprocketId {
                id: String
            }

            @error("server")
            structure ServiceFooError {
                why: String
            }
            """;
        try {
            Files.write(modelFile, modelContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            fail("Failed to write model file: " + e.getMessage());
        }
        return tempDir;
    }
}