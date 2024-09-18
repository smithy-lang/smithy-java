/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

final class Log4j2Logger extends ExtendedLoggerWrapper implements InternalLogger {

    static final class Factory implements InternalLogger.Factory {

        @Override
        public InternalLogger getLogger(String name) {
            return new Log4j2Logger((ExtendedLogger) LogManager.getLogger(name));
        }
    }

    private Log4j2Logger(ExtendedLogger logger) {
        super(logger, logger.getName(), logger.getMessageFactory());
    }

    @Override
    public void log(Level level, String message, Object... params) {
        log(convert(level), message, params);
    }

    private org.apache.logging.log4j.Level convert(Level level) {
        return switch (level) {
            case TRACE -> org.apache.logging.log4j.Level.TRACE;
            case DEBUG -> org.apache.logging.log4j.Level.DEBUG;
            case INFO -> org.apache.logging.log4j.Level.INFO;
            case WARN -> org.apache.logging.log4j.Level.WARN;
            case ERROR -> org.apache.logging.log4j.Level.ERROR;
            case FATAL -> org.apache.logging.log4j.Level.FATAL;
        };
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        log(convert(level), message, throwable);
    }

    @Override
    public void log(Level level, String message, Object p0) {
        log(convert(level), message, p0);

    }

    @Override
    public void log(Level level, String message, Object p0, Object p1) {
        log(convert(level), message, p0, p1);

    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2) {
        log(convert(level), message, p0, p1, p2);

    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2, Object p3) {
        log(convert(level), message, p0, p1, p2, p3);

    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        log(convert(level), message, p0, p1, p2, p3, p4);

    }

    @Override
    public void log(
        Level level,
        String message,
        Object p0,
        Object p1,
        Object p2,
        Object p3,
        Object p4,
        Object p5
    ) {
        log(convert(level), message, p0, p1, p2, p3, p4, p5);

    }

    @Override
    public void log(
        Level level,
        String message,
        Object p0,
        Object p1,
        Object p2,
        Object p3,
        Object p4,
        Object p5,
        Object p6
    ) {
        log(convert(level), message, p0, p1, p2, p3, p4, p5, p6);

    }

    @Override
    public void log(
        Level level,
        String message,
        Object p0,
        Object p1,
        Object p2,
        Object p3,
        Object p4,
        Object p5,
        Object p6,
        Object p7
    ) {
        log(convert(level), message, p0, p1, p2, p3, p4, p5, p6, p7);

    }

    @Override
    public void log(
        Level level,
        String message,
        Object p0,
        Object p1,
        Object p2,
        Object p3,
        Object p4,
        Object p5,
        Object p6,
        Object p7,
        Object p8
    ) {
        log(convert(level), message, p0, p1, p2, p3, p4, p5, p6, p7, p8);

    }

    @Override
    public void log(
        Level level,
        String message,
        Object p0,
        Object p1,
        Object p2,
        Object p3,
        Object p4,
        Object p5,
        Object p6,
        Object p7,
        Object p8,
        Object p9
    ) {
        log(convert(level), message, p0, p1, p2, p3, p4, p5, p6, p7, p9);
    }
}
