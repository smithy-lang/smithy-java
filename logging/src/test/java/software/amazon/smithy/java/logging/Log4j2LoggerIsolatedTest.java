/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Log4j2LoggerIsolatedTest {

    private static final StringWriter WRITER = new StringWriter();
    private static WriterAppender APPENDER;

    @BeforeAll
    public static void setupLoggers() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration cfg = ctx.getConfiguration();
        cfg.getLoggerConfig("").setLevel(org.apache.logging.log4j.Level.TRACE);
        APPENDER = WriterAppender.newBuilder()
                .setTarget(WRITER)
                .setName("Log4j2LoggerTestAppender")
                .setLayout(PatternLayout.newBuilder().withPattern("%level %m%n").build())
                .build();
        cfg.addAppender(APPENDER);

        cfg.start();

        ctx.getRootLogger()
                .removeAppender(
                        ctx.getRootLogger().getAppenders().values().stream().findAny().get());
        ctx.getRootLogger().addAppender(APPENDER);

        ctx.updateLoggers();
    }

    @AfterEach
    public void tearDown() {
        WRITER.getBuffer().setLength(0);
    }

    @Test
    void smokeTest() {
        InternalLogger logger = InternalLogger.getLogger(Log4j2LoggerIsolatedTest.class);
        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = WRITER.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(5);
        assertThat(lines[0]).startsWith("ERROR test1");
        assertThat(lines[1]).startsWith("WARN test2: abc");
        assertThat(lines[2]).startsWith("FATAL a: test3: b c");
        assertThat(lines[3]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[4]).contains(
                "at software.amazon.smithy.java.logging.Log4j2LoggerIsolatedTest.smokeTest(Log4j2LoggerIsolatedTest.java:");
    }

    @Test
    void dynamicLevelSmokeTest() {
        InternalLogger logger = InternalLogger.getLogger(Log4j2LoggerIsolatedTest.class);
        logger.log(InternalLogger.Level.ERROR, "test1");
        logger.log(InternalLogger.Level.WARN, "test2: {}", "abc");
        logger.log(
                InternalLogger.Level.FATAL,
                "{}: test3: {} {}",
                "a",
                "b",
                "c",
                new NullPointerException("BANG!"));

        var lines = WRITER.toString().split(System.lineSeparator());
        assertThat(lines).hasSizeGreaterThan(5);
        assertThat(lines[0]).startsWith("ERROR test1");
        assertThat(lines[1]).startsWith("WARN test2: abc");
        assertThat(lines[2]).startsWith("FATAL a: test3: b c");
        assertThat(lines[3]).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines[4]).contains(
                "at software.amazon.smithy.java.logging.Log4j2LoggerIsolatedTest.dynamicLevelSmokeTest(Log4j2LoggerIsolatedTest.java:");
    }
}
