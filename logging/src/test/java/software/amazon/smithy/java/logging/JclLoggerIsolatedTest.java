/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JclLoggerIsolatedTest {

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
        JclLog.warnEnabled = true;
    }

    @Test
    void smokeTest() {
        InternalLogger logger = InternalLogger.getLogger(JclLoggerIsolatedTest.class);
        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = JclLog.BUFFER.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(5);
        assertThat(lines[0]).startsWith("ERROR test1");
        assertThat(lines[1]).startsWith("WARN test2: abc");
        assertThat(lines[2]).startsWith("FATAL a: test3: b c");
        assertThat(lines[3]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[4]).contains(
            "at software.amazon.smithy.java.logging.JclLoggerIsolatedTest.smokeTest(JclLoggerIsolatedTest.java:"
        );
    }

    @Test
    void levelTest() {
        InternalLogger logger = InternalLogger.getLogger(JclLoggerIsolatedTest.class);
        JclLog.warnEnabled = false;
        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = JclLog.BUFFER.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(4);
        assertThat(lines[0]).startsWith("ERROR test1");
        assertThat(lines[1]).startsWith("FATAL a: test3: b c");
        assertThat(lines[2]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[3]).contains(
            "at software.amazon.smithy.java.logging.JclLoggerIsolatedTest.levelTest(JclLoggerIsolatedTest.java:"
        );
    }

    @Test
    void dynamicLevelTest() {
        InternalLogger logger = InternalLogger.getLogger(JclLoggerIsolatedTest.class);
        JclLog.warnEnabled = false;
        logger.log(InternalLogger.Level.ERROR, "test1");
        logger.log(InternalLogger.Level.WARN, "test2: {}", "abc");
        logger.log(
            InternalLogger.Level.FATAL,
            "{}: test3: {} {}",
            "a",
            "b",
            "c",
            new NullPointerException("BANG!")
        );

        var lines = JclLog.BUFFER.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(4);
        assertThat(lines[0]).startsWith("ERROR test1");
        assertThat(lines[1]).startsWith("FATAL a: test3: b c");
        assertThat(lines[2]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[3]).contains(
            "at software.amazon.smithy.java.logging.JclLoggerIsolatedTest.dynamicLevelTest(JclLoggerIsolatedTest.java:"
        );
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
        public void release() {
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public void setAttribute(String name, Object value) {
        }
    }

    static final class JclLog implements Log {
        static final StringWriter BUFFER = new StringWriter();
        static final PrintWriter WRITER = new PrintWriter(BUFFER);

        static volatile boolean warnEnabled = true;

        public JclLog() {
        }

        @Override
        public void debug(Object message) {
            WRITER.write("DEBUG ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
        }

        @Override
        public void debug(Object message, Throwable t) {
            WRITER.write("DEBUG ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
            if (t != null) {
                t.printStackTrace(WRITER);
            }
        }

        @Override
        public void info(Object message) {
            WRITER.write("INFO ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
        }

        @Override
        public void info(Object message, Throwable t) {
            WRITER.write("INFO ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
            if (t != null) {
                t.printStackTrace(WRITER);
            }
        }

        @Override
        public void warn(Object message) {
            if (!warnEnabled) {
                throw new IllegalStateException();
            }
            WRITER.write("WARN ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
        }

        @Override
        public void warn(Object message, Throwable t) {
            if (!warnEnabled) {
                throw new IllegalStateException();
            }
            WRITER.write("WARN ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
            if (t != null) {
                t.printStackTrace(WRITER);
            }
        }

        @Override
        public void error(Object message) {
            WRITER.write("ERROR ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
        }

        @Override
        public void error(Object message, Throwable t) {
            WRITER.write("ERROR ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
            if (t != null) {
                t.printStackTrace(WRITER);
            }
        }

        @Override
        public void fatal(Object message) {
            WRITER.write("FATAL ");
            WRITER.write(String.valueOf(message));
            WRITER.write(System.lineSeparator());
        }

        @Override
        public void fatal(Object message, Throwable t) {
            WRITER.write("FATAL ");
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
            return false;
        }

        @Override
        public boolean isWarnEnabled() {
            return warnEnabled;
        }

        @Override
        public void trace(Object message) {
        }

        @Override
        public void trace(Object message, Throwable t) {
        }
    }

}
