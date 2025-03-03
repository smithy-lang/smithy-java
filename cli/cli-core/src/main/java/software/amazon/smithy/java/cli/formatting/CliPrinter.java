/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.formatting;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Handles text output of the CLI.
 */
public sealed interface CliPrinter extends Appendable, Flushable, Closeable {

    @Override
    CliPrinter append(char c);

    @Override
    default CliPrinter append(CharSequence csq) {
        return append(csq, 0, csq.length());
    }

    @Override
    CliPrinter append(CharSequence csq, int start, int end);

    /**
     * Flushes any buffers in the printer.
     */
    default void flush() {}

    /**
     * @return Output stream this writer writes to
     */
    OutputStream getOutputStream();

    /**
     * Create a new CliPrinter from an OutputStream.
     *
     * @param stream OutputStream to write to.
     * @return Returns the created CliPrinter.
     */
    static CliPrinter fromOutputStream(OutputStream stream) {
        var writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        return new OutputStreamPrinter(writer, stream);
    }

    record OutputStreamPrinter(OutputStreamWriter writer, OutputStream stream) implements CliPrinter {
        @Override
        public CliPrinter append(char c) {
            try {
                writer.append(c);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        @Override
        public CliPrinter append(CharSequence csq) {
            try {
                writer.append(csq);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        @Override
        public CliPrinter append(CharSequence csq, int start, int end) {
            try {
                writer.append(csq, start, end);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        @Override
        public void flush() {
            try {
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // TODO: Is there a cleaner approach?
        @Override
        public OutputStream getOutputStream() {
            return stream;
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }
}
