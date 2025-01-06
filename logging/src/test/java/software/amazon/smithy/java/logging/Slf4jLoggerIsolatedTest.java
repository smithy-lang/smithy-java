/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class Slf4jLoggerIsolatedTest {

    private static final ListAppender<ILoggingEvent> LIST_APPENDER = new ListAppender<>();

    @BeforeAll
    public static void setupLoggers() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(LIST_APPENDER);
        LIST_APPENDER.start();
    }

    @AfterEach
    public void clean() {
        LIST_APPENDER.list.clear();
    }

    @Test
    void smokeTest() {
        InternalLogger logger = InternalLogger.getLogger(Slf4jLoggerIsolatedTest.class);

        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        assertEquals(ch.qos.logback.classic.Level.ERROR, LIST_APPENDER.list.get(0).getLevel());
        assertEquals("test1", LIST_APPENDER.list.get(0).getFormattedMessage());
        assertEquals(Slf4jLoggerIsolatedTest.class.getName(), LIST_APPENDER.list.get(0).getLoggerName());

        assertEquals(ch.qos.logback.classic.Level.WARN, LIST_APPENDER.list.get(1).getLevel());
        assertEquals("test2: abc", LIST_APPENDER.list.get(1).getFormattedMessage());

        assertEquals(ch.qos.logback.classic.Level.ERROR, LIST_APPENDER.list.get(2).getLevel());
        assertEquals("a: test3: b c", LIST_APPENDER.list.get(2).getFormattedMessage());
        assertEquals(
                NullPointerException.class.getName(),
                LIST_APPENDER.list.get(2).getThrowableProxy().getClassName());
        assertEquals("BANG!", LIST_APPENDER.list.get(2).getThrowableProxy().getMessage());
        assertEquals("FATAL", LIST_APPENDER.list.get(2).getMarkerList().get(0).getName());
    }

    @Test
    void dynamicLevelSmokeTest() {
        InternalLogger logger = InternalLogger.getLogger(Slf4jLoggerIsolatedTest.class);
        logger.log(InternalLogger.Level.ERROR, "test1");
        logger.log(InternalLogger.Level.WARN, "test2: {}", "abc");
        logger.log(
                InternalLogger.Level.FATAL,
                "{}: test3: {} {}",
                "a",
                "b",
                "c",
                new NullPointerException("BANG!"));

        assertEquals(ch.qos.logback.classic.Level.ERROR, LIST_APPENDER.list.get(0).getLevel());
        assertEquals("test1", LIST_APPENDER.list.get(0).getFormattedMessage());
        assertEquals(Slf4jLoggerIsolatedTest.class.getName(), LIST_APPENDER.list.get(0).getLoggerName());

        assertEquals(ch.qos.logback.classic.Level.WARN, LIST_APPENDER.list.get(1).getLevel());
        assertEquals("test2: abc", LIST_APPENDER.list.get(1).getFormattedMessage());

        assertEquals(ch.qos.logback.classic.Level.ERROR, LIST_APPENDER.list.get(2).getLevel());
        assertEquals("a: test3: b c", LIST_APPENDER.list.get(2).getFormattedMessage());
        assertEquals(
                NullPointerException.class.getName(),
                LIST_APPENDER.list.get(2).getThrowableProxy().getClassName());
        assertEquals("BANG!", LIST_APPENDER.list.get(2).getThrowableProxy().getMessage());
        assertEquals("FATAL", LIST_APPENDER.list.get(2).getMarkerList().get(0).getName());
    }
}
