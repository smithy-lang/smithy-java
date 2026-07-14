/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A {@link java.util.logging} handler that mirrors log records into {@link InspectorState} so they
 * become queryable over MCP. Attach it to the root JUL logger to intercept all logs when the SDK's
 * logging backend is JDK logging (smithy-java's {@code InternalLogger} falls back to it).
 *
 * <p>Log4j2/SLF4J backends need their own appender; this handler covers the JUL fallback and is the
 * simplest thing that demonstrates "intercept all logs, make them queryable."
 */
public final class InspectorLogHandler extends Handler {

    private final InspectorState state;

    public InspectorLogHandler(InspectorState state) {
        this.state = state;
    }

    /**
     * Attach a handler for the given state to the root logger and return it.
     *
     * <p>Also lowers the root logger level to {@code ALL} so DEBUG/TRACE records actually reach the
     * handler — a logger gates records by level before dispatching to handlers, so a handler set to
     * {@code ALL} still sees nothing below the root logger's own (default INFO) level. This is a
     * global, dev/eval-only side effect.
     */
    public static InspectorLogHandler attachToRoot(InspectorState state) {
        var handler = new InspectorLogHandler(state);
        handler.setLevel(Level.ALL);
        var root = Logger.getLogger("");
        root.setLevel(Level.ALL);
        root.addHandler(handler);
        return handler;
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null) {
            return;
        }
        String thrown = null;
        if (record.getThrown() != null) {
            var sw = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(sw));
            thrown = sw.toString();
        }
        state.addLog(new InspectorState.LogEntry(
                mapLevel(record.getLevel()),
                record.getLoggerName(),
                record.getMessage(),
                record.getMillis(),
                thrown,
                state.currentCallId()));
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
        Logger.getLogger("").removeHandler(this);
    }

    private static String mapLevel(Level level) {
        int v = level.intValue();
        if (v >= Level.SEVERE.intValue()) {
            return "ERROR";
        } else if (v >= Level.WARNING.intValue()) {
            return "WARN";
        } else if (v >= Level.INFO.intValue()) {
            return "INFO";
        } else if (v >= Level.FINE.intValue()) {
            return "DEBUG";
        }
        return "TRACE";
    }
}
