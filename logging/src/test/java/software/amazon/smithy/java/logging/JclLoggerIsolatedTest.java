/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

public class JclLoggerIsolatedTest extends LoggerTestBase {

    @BeforeAll
    public static void setupLoggers() {
        System.setProperty("org.apache.commons.logging.LogFactory", JclLogFactory.class.getName());
        LogFactory.release(Thread.currentThread().getContextClassLoader());
    }

    @AfterAll
    public static void tearDownLoggers() {
        System.clearProperty("org.apache.commons.logging.LogFactory");
        LogFactory.release(Thread.currentThread().getContextClassLoader());
    }

    @AfterEach
    public void tearDown() {
        JclLog.BUFFER.getBuffer().setLength(0);
    }

    @Override
    protected List<String> getLogLines() {
        return Arrays.asList(JclLog.BUFFER.toString().split(System.lineSeparator()));
    }

    @Override
    protected void disableWarnLevel() {
        JclLog.warnEnabled = false;
    }

    @Override
    protected void enableWarnLevel() {
        JclLog.warnEnabled = true;
    }

    public static final class JclLogFactory extends LogFactory {
        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public String[] getAttributeNames() {
            return new String[0];
        }

        @Override
        public Log getInstance(Class clazz) throws LogConfigurationException {
            return new JclLog();
        }

        @Override
        public Log getInstance(String name) throws LogConfigurationException {
            return new JclLog();
        }

        @Override
        public void release() {}

        @Override
        public void removeAttribute(String name) {}

        @Override
        public void setAttribute(String name, Object value) {}
    }

    static final class JclLog implements Log {
        static final StringWriter BUFFER = new StringWriter();
        static final PrintWriter WRITER = new PrintWriter(BUFFER);

        static volatile boolean warnEnabled = true;

        public JclLog() {}

        @Override
        public void debug(Object message) {
            write("DEBUG", message);
        }

        @Override
        public void debug(Object message, Throwable t) {
            write("DEBUG", message, t);
        }

        @Override
        public void info(Object message) {
            write("INFO", message);
        }

        @Override
        public void info(Object message, Throwable t) {
            write("INFO", message, t);
        }

        @Override
        public void warn(Object message) {
            if (!warnEnabled) {
                throw new IllegalStateException();
            }
            write("WARN", message);
        }

        @Override
        public void warn(Object message, Throwable t) {
            if (!warnEnabled) {
                throw new IllegalStateException();
            }
            write("WARN", message, t);
        }

        @Override
        public void error(Object message) {
            write("ERROR", message);
        }

        @Override
        public void error(Object message, Throwable t) {
            write("ERROR", message, t);
        }

        @Override
        public void fatal(Object message) {
            write("FATAL", message);
        }

        @Override
        public void fatal(Object message, Throwable t) {
            write("FATAL", message, t);
        }

        @Override
        public void trace(Object message) {
            write("TRACE", message);
        }

        @Override
        public void trace(Object message, Throwable t) {
            write("TRACE", message, t);
        }

        private void write(String level, Object message) {
            write(level, message, null);
        }

        private void write(String level, Object message, Throwable t) {
            WRITER.write(level + " ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
            if (t != null) {
                t.printStackTrace(WRITER);
            }
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isFatalEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled() {
            return warnEnabled;
        }
    }

}
