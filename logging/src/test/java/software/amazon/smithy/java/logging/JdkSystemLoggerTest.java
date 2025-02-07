/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

class JdkSystemLoggerTest extends LoggerTestBase {

    private static TestLogger testLog;

    @BeforeAll
    public static void setupLoggers() {
        testLog = new TestLogger(JdkSystemLoggerTest.class.getName());
        LogManager.getLogManager().addLogger(testLog);
    }

    @AfterEach
    public void tearDown() {
        testLog.buffer.getBuffer().setLength(0);
        testLog.warnEnabled = true;
    }

    @Override
    protected List<String> getLogLines() {
        return Arrays.asList(testLog.buffer.toString().split(System.lineSeparator()));
    }

    @Override
    protected String getLevelString(InternalLogger.Level level) {
        return switch (level) {
            case WARN -> "WARNING";
            case ERROR -> "SEVERE";
            case FATAL -> "SEVERE [FATAL]";
            case TRACE -> "FINER";
            case DEBUG -> "FINE";
            default -> super.getLevelString(level);
        };
    }

    @Override
    protected void disableWarnLevel() {
        testLog.warnEnabled = false;
    }

    @Override
    protected void enableWarnLevel() {
        testLog.warnEnabled = true;
    }

    private static final class TestLogger extends java.util.logging.Logger {
        final StringWriter buffer = new StringWriter();
        final PrintWriter writer = new PrintWriter(buffer);

        volatile boolean warnEnabled = true;

        TestLogger(String name) {
            super(name, null);
        }

        @Override
        public void log(Level level, String msg) {
            if (level == java.util.logging.Level.WARNING && !warnEnabled) {
                throw new IllegalStateException();
            }
            writer.write(level + " ");
            writer.write(msg);
            writer.write(System.lineSeparator());
        }

        @Override
        public void log(Level level, String msg, Throwable thrown) {
            if (level == java.util.logging.Level.WARNING && !warnEnabled) {
                throw new IllegalStateException();
            }
            writer.write(level + " ");
            writer.write(msg);
            writer.write(System.lineSeparator());
            if (thrown != null) {
                thrown.printStackTrace(writer);
            }
        }

        @Override
        public boolean isLoggable(Level level) {
            return level != java.util.logging.Level.WARNING || warnEnabled;
        }
    }

}
