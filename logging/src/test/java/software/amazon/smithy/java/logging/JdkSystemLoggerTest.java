/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JdkSystemLoggerTest {

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

    @Test
    void smokeTest() {
        InternalLogger logger = InternalLogger.getLogger(JdkSystemLoggerTest.class);
        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = testLog.buffer.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(5);
        assertThat(lines[0]).startsWith("SEVERE test1");
        assertThat(lines[1]).startsWith("WARNING test2: abc");
        assertThat(lines[2]).startsWith("SEVERE [FATAL] a: test3: b c");
        assertThat(lines[3]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[4]).contains(
            "at software.amazon.smithy.java.logging.JdkSystemLoggerTest.smokeTest(JdkSystemLoggerTest.java:"
        );
    }

    @Test
    void levelTest() {
        InternalLogger logger = InternalLogger.getLogger(JdkSystemLoggerTest.class);
        testLog.warnEnabled = false;
        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = testLog.buffer.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(4);
        assertThat(lines[0]).startsWith("SEVERE test1");
        assertThat(lines[1]).startsWith("SEVERE [FATAL] a: test3: b c");
        assertThat(lines[2]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[3]).contains(
            "at software.amazon.smithy.java.logging.JdkSystemLoggerTest.levelTest(JdkSystemLoggerTest.java:"
        );
    }

    @Test
    void dynamicLevelTest() {
        InternalLogger logger = InternalLogger.getLogger(JdkSystemLoggerTest.class);
        testLog.warnEnabled = false;
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

        var lines = testLog.buffer.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(4);
        assertThat(lines[0]).startsWith("SEVERE test1");
        assertThat(lines[1]).startsWith("SEVERE [FATAL] a: test3: b c");
        assertThat(lines[2]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[3]).contains(
            "at software.amazon.smithy.java.logging.JdkSystemLoggerTest.dynamicLevelTest(JdkSystemLoggerTest.java:"
        );
    }

    private static final class TestLogger extends java.util.logging.Logger {
        final StringWriter buffer = new StringWriter();
        final PrintWriter writer = new PrintWriter(buffer);

        boolean warnEnabled = true;

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
