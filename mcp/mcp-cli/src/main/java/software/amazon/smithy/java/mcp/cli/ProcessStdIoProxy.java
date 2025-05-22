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
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A simple proxy that forwards standard input to a subprocess and the subprocess's
 * standard output back to the original standard output. It doesn't interpret
 * the data flowing through the streams.
 */
public final class ProcessStdIoProxy {
    private static final InternalLogger LOG = InternalLogger.getLogger(ProcessStdIoProxy.class);

    private final ProcessBuilder processBuilder;
    private Process process;
    private Thread inputThread;
    private Thread outputThread;
    private Thread errorThread;
    private volatile boolean running = false;

    private ProcessStdIoProxy(Builder builder) {
        processBuilder = new ProcessBuilder();
        processBuilder.command().add(builder.command);

        if (builder.arguments != null) {
            processBuilder.command().addAll(builder.arguments);
        }

        // Set environment variables if provided
        if (builder.environmentVariables != null) {
            processBuilder.environment().putAll(builder.environmentVariables);
        }

        processBuilder.redirectErrorStream(false); // Keep stderr separate
    }

    /**
     * Builder for creating ProcessStdIoProxy instances.
     */
    public static class Builder {
        private String command;
        private List<String> arguments;
        private Map<String, String> environmentVariables;

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
         * Builds a new ProcessStdIoProxy instance.
         *
         * @return A new ProcessStdIoProxy instance
         * @throws IllegalArgumentException if command is null or empty
         */
        public ProcessStdIoProxy build() {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Command must be provided");
            }
            return new ProcessStdIoProxy(this);
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
            running = true;

            // Thread to forward stdin to process
            inputThread = Thread.ofVirtual()
                    .name("process-stdin-forwarder")
                    .start(() -> {
                        try (OutputStream processInput = process.getOutputStream();
                                InputStream systemInput = System.in) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while (running && process.isAlive() && (bytesRead = systemInput.read(buffer)) != -1) {
                                processInput.write(buffer, 0, bytesRead);
                                processInput.flush();
                            }
                        } catch (IOException e) {
                            if (running) {
                                LOG.error("Error forwarding stdin to process", e);
                            }
                        }
                    });

            // Thread to forward process stdout to system stdout
            outputThread = Thread.ofVirtual()
                    .name("process-stdout-forwarder")
                    .start(() -> {
                        try (InputStream processOutput = process.getInputStream()) {
                            OutputStream systemOutput = System.out;
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while (running && process.isAlive() && (bytesRead = processOutput.read(buffer)) != -1) {
                                systemOutput.write(buffer, 0, bytesRead);
                                systemOutput.flush();
                            }
                        } catch (IOException e) {
                            if (running) {
                                LOG.error("Error forwarding process stdout", e);
                            }
                        }
                    });

            // Thread to forward process stderr to system stderr
            errorThread = Thread.ofVirtual()
                    .name("process-stderr-forwarder")
                    .start(() -> {
                        try (InputStream processError = process.getErrorStream();
                                OutputStream systemError = System.err) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while (running && process.isAlive() && (bytesRead = processError.read(buffer)) != -1) {
                                systemError.write(buffer, 0, bytesRead);
                                systemError.flush();
                            }
                        } catch (IOException e) {
                            if (running) {
                                LOG.error("Error forwarding process stderr", e);
                            }
                        }
                    });

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
            running = false;
            if (process != null && process.isAlive()) {
                try {
                    // Interrupt the threads
                    if (inputThread != null && inputThread.isAlive()) {
                        inputThread.interrupt();
                    }
                    if (outputThread != null && outputThread.isAlive()) {
                        outputThread.interrupt();
                    }
                    if (errorThread != null && errorThread.isAlive()) {
                        errorThread.interrupt();
                    }

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
        });
    }

    /**
     * Checks if the process is currently running.
     *
     * @return true if the process is running, false otherwise
     */
    public boolean isRunning() {
        return running && process != null && process.isAlive();
    }

    /**
     * Waits for the process to exit.
     *
     * @return The exit code of the process
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public int waitFor() throws InterruptedException {
        if (process == null) {
            throw new IllegalStateException("Process has not been started");
        }
        return process.waitFor();
    }

    public static void main(String[] args) {
        ProcessStdIoProxy.builder().command("amzn-mcp").build().start();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {

        }
    }
}
