/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A simple proxy that forwards standard input to a subprocess and the subprocess's
 * standard output back to the original standard output. It doesn't interpret
 * the data flowing through the streams.
 */
public final class ProcessIoProxy {
    private static final InternalLogger LOG = InternalLogger.getLogger(ProcessIoProxy.class);

    private final ProcessBuilder processBuilder;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final OutputStream errorStream;
    private Process process;
    private Thread inputThread;
    private Thread outputThread;
    private Thread errorThread;
    private AtomicBoolean running = new AtomicBoolean(false);

    private ProcessIoProxy(Builder builder) {
        processBuilder = new ProcessBuilder();
        processBuilder.command().add(builder.command);

        if (builder.arguments != null) {
            processBuilder.command().addAll(builder.arguments);
        }

        // Set environment variables if provided
        if (builder.environmentVariables != null) {
            processBuilder.environment().putAll(builder.environmentVariables);
        }

        this.inputStream = builder.inputStream;
        this.outputStream = builder.outputStream;
        this.errorStream = builder.errorStream;

        processBuilder.redirectErrorStream(false); // Keep stderr separate
    }

    /**
     * Builder for creating ProcessStdIoProxy instances.
     */
    public static class Builder {
        private String command;
        private List<String> arguments;
        private Map<String, String> environmentVariables;
        private InputStream inputStream;
        private OutputStream outputStream;
        private OutputStream errorStream;

        /**
         * Sets the command to execute.
         *
         * @param command The command to execute
         * @return This builder for method chaining
         */
        public Builder command(String command) {
            this.command = command;
            return this;
        }

        /**
         * Sets the command-line arguments.
         *
         * @param arguments The command-line arguments
         * @return This builder for method chaining
         */
        public Builder arguments(List<String> arguments) {
            this.arguments = arguments;
            return this;
        }

        /**
         * Sets the environment variables for the process.
         *
         * @param environmentVariables The environment variables
         * @return This builder for method chaining
         */
        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        /**
         * Customize IoStreams (input, output, error).
         * Any stream that is null will use the system default (System.in, System.out, System.err).
         *
         * @param input The input stream to use (defaults to System.in if null)
         * @param output The output stream to use (defaults to System.out if null)
         * @param error The error stream to use (defaults to System.err if null)
         * @return This builder for method chaining
         */
        public Builder streams(InputStream input, OutputStream output, OutputStream error) {
            this.inputStream = input == null ? System.in : input;
            this.outputStream = output == null ? System.out : output;
            this.errorStream = error == null ? System.err : error;
            return this;
        }

        /**
         * Builds a new ProcessStdIoProxy instance.
         *
         * @return A new ProcessStdIoProxy instance
         * @throws IllegalArgumentException if command is null or empty
         */
        public ProcessIoProxy build() {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Command must be provided");
            }
            return new ProcessIoProxy(this);
        }
    }

    /**
     * Creates a new builder for ProcessStdIoProxy.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Buffer size for reading/writing data
    private static final int BUFFER_SIZE = 4096;

    /**
     * Creates a thread that forwards data from an input stream to an output stream.
     *
     * @param input The source input stream
     * @param output The target output stream
     * @param name The name of the thread
     * @param errorMessage Error message to log if an exception occurs
     * @param closeOutput Whether to close the output stream when done
     * @return The created thread
     */
    private static Thread createForwardingThread(
            Process process,
            InputStream input,
            OutputStream output,
            String name,
            String errorMessage,
            boolean closeOutput,
            AtomicBoolean running
    ) {
        return Thread.ofVirtual()
                .name(name)
                .start(() -> {
                    try {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while (running.get() && process.isAlive() && (bytesRead = input.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                            output.flush();
                        }
                    } catch (IOException e) {
                        if (running.get()) {
                            LOG.error(errorMessage, e);
                        }
                    } finally {
                        if (closeOutput) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                LOG.error("Error closing stream", e);
                            }
                        }
                    }
                });
    }

    /**
     * Starts the proxy, launching the subprocess and beginning to forward stdin/stdout.
     *
     * @throws RuntimeException If there is an error starting the process
     */
    public synchronized void start() {
        if (process != null && process.isAlive()) {
            return;
        }
        try {
            process = processBuilder.start();
            running.getAndSet(true);

            // Thread to forward input to process
            inputThread = createForwardingThread(
                    process,
                    inputStream,
                    process.getOutputStream(),
                    "process-stdin-forwarder",
                    "Error forwarding input to process",
                    true, // Close the process input stream when done
                    running);

            // Thread to forward process stdout to output
            outputThread = createForwardingThread(
                    process,
                    process.getInputStream(),
                    outputStream,
                    "process-stdout-forwarder",
                    "Error forwarding process stdout",
                    false, // Don't close the output stream
                    running);

            // Thread to forward process stderr to error
            errorThread = createForwardingThread(
                    process,
                    process.getErrorStream(),
                    errorStream,
                    "process-stderr-forwarder",
                    "Error forwarding process stderr",
                    false, // Don't close the error stream
                    running);

        } catch (IOException e) {
            throw new RuntimeException("Failed to start process: " + e.getMessage(), e);
        }
    }

    /**
     * Shuts down the proxy, stopping all forwarding and terminating the subprocess.
     *
     * @return A CompletableFuture that completes when shutdown is finished
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                if (process != null && process.isAlive()) {
                    try {
                        // Interrupt the threads
                        interruptThread(inputThread);
                        interruptThread(outputThread);
                        interruptThread(errorThread);

                        // Destroy the process
                        process.destroy();

                        // Wait for termination with timeout
                        if (!process.waitFor(5, SECONDS)) {
                            // Force kill if it doesn't terminate gracefully
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        LOG.error("Error shutting down process", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
            running.set(false);
        });
    }

    private static void interruptThread(Thread thread) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }
}
