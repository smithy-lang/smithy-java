/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.spi.LoggingEventBuilder;

final class Slf4jLogger implements InternalLogger {

    private static final Marker FATAL = MarkerFactory.getMarker("FATAL");

    private final Logger log;

    Slf4jLogger(Logger log) {
        this.log = log;
    }

    static final class Factory implements InternalLogger.Factory {
        @Override
        public InternalLogger getLogger(String name) {
            return new Slf4jLogger(LoggerFactory.getLogger(name));
        }
    }

    private org.slf4j.event.Level convert(Level level) {
        return switch (level) {
            case TRACE -> org.slf4j.event.Level.TRACE;
            case DEBUG -> org.slf4j.event.Level.DEBUG;
            case INFO -> org.slf4j.event.Level.INFO;
            case WARN -> org.slf4j.event.Level.WARN;
            case ERROR, FATAL -> org.slf4j.event.Level.ERROR;
        };
    }

    private boolean isEnabled(Level level) {
        return switch (level) {
            case TRACE -> log.isTraceEnabled();
            case DEBUG -> log.isDebugEnabled();
            case INFO -> log.isInfoEnabled();
            case WARN -> log.isWarnEnabled();
            case ERROR -> log.isErrorEnabled();
            case FATAL -> log.isErrorEnabled(FATAL);
        };
    }

    @Override
    public void log(Level level, String message, Object... params) {
        if (isEnabled(level)) {
            logBuilder(level).log(message, params);
        }
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        if (isEnabled(level)) {
            logBuilder(level).setMessage(message).setCause(throwable).log();
        }

    }

    @Override
    public void log(Level level, String message, Object p0) {
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0);
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1) {
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1);
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2) {
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2);
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2, p3);
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2, p3, p4);
        }
    }

    private LoggingEventBuilder logBuilder(Level level) {
        var builder = log.atLevel(convert(level));
        if (level == Level.FATAL) {
            builder = builder.addMarker(FATAL);
        }
        return builder;
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
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2, p3, p4, p5);
        }
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
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2, p3, p4, p5, p6);
        }
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
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
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
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
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
        if (isEnabled(level)) {
            logBuilder(level).log(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
    }

    @Override
    public void trace(String message, Object... params) {
        if (log.isTraceEnabled()) {
            log.trace(message, params);
        }
    }

    @Override
    public void trace(String message, Throwable throwable) {
        if (log.isTraceEnabled()) {
            log.trace(message, throwable);
        }
    }

    @Override
    public void trace(String message, Object p0) {
        if (log.isTraceEnabled()) {
            log.trace(message, p0);
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1) {
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1);
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2) {
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2);
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2, p3);
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2, p3, p4);
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2, p3, p4, p5);
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2, p3, p4, p5, p6);
        }
    }

    @Override
    public void trace(
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
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }

    @Override
    public void trace(
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
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }

    @Override
    public void trace(
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
        if (log.isTraceEnabled()) {
            log.trace(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
    }

    @Override
    public void debug(String message, Object... params) {
        if (log.isDebugEnabled()) {
            log.debug(message, params);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (log.isDebugEnabled()) {
            log.debug(message, throwable);
        }
    }

    @Override
    public void debug(String message, Object p0) {
        if (log.isDebugEnabled()) {
            log.debug(message, p0);
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1) {
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1);
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2) {
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2);
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2, p3);
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2, p3, p4);
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2, p3, p4, p5);
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2, p3, p4, p5, p6);
        }
    }

    @Override
    public void debug(
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
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }

    @Override
    public void debug(
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
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }

    @Override
    public void debug(
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
        if (log.isDebugEnabled()) {
            log.debug(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
    }

    @Override
    public void info(String message, Object... params) {
        if (log.isInfoEnabled()) {
            log.info(message, params);
        }
    }

    @Override
    public void info(String message, Throwable throwable) {
        if (log.isInfoEnabled()) {
            log.info(message, throwable);
        }
    }

    @Override
    public void info(String message, Object p0) {
        if (log.isInfoEnabled()) {
            log.info(message, p0);
        }
    }

    @Override
    public void info(String message, Object p0, Object p1) {
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1);
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2) {
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2);
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2, p3);
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2, p3, p4);
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2, p3, p4, p5);
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2, p3, p4, p5, p6);
        }
    }

    @Override
    public void info(
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
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }

    @Override
    public void info(
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
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }

    @Override
    public void info(
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
        if (log.isInfoEnabled()) {
            log.info(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
    }

    @Override
    public void warn(String message, Object... params) {
        if (log.isWarnEnabled()) {
            log.warn(message, params);
        }
    }

    @Override
    public void warn(String message, Throwable throwable) {
        if (log.isWarnEnabled()) {
            log.warn(message, throwable);
        }
    }

    @Override
    public void warn(String message, Object p0) {
        if (log.isWarnEnabled()) {
            log.warn(message, p0);
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1) {
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1);
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2) {
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2);
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2, p3);
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2, p3, p4);
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2, p3, p4, p5);
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2, p3, p4, p5, p6);
        }
    }

    @Override
    public void warn(
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
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }

    @Override
    public void warn(
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
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }

    @Override
    public void warn(
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
        if (log.isWarnEnabled()) {
            log.warn(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }

    }

    @Override
    public void error(String message, Object... params) {
        if (log.isErrorEnabled()) {
            log.error(message, params);
        }

    }

    @Override
    public void error(String message, Throwable throwable) {
        if (log.isErrorEnabled()) {
            log.error(message, throwable);
        }
    }

    @Override
    public void error(String message, Object p0) {
        if (log.isErrorEnabled()) {
            log.error(message, p0);
        }
    }

    @Override
    public void error(String message, Object p0, Object p1) {
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1);
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2) {
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2);
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2, p3);
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2, p3, p4);
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2, p3, p4, p5);
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2, p3, p4, p5, p6);
        }
    }

    @Override
    public void error(
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
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }

    @Override
    public void error(
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
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }

    @Override
    public void error(
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
        if (log.isErrorEnabled()) {
            log.error(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
    }

    @Override
    public void fatal(String message, Object... params) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, params);
        }
    }

    @Override
    public void fatal(String message, Throwable throwable) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, throwable);
        }
    }

    @Override
    public void fatal(String message, Object p0) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0);
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1);
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2);
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2, p3);
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2, p3, p4);
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2, p3, p4, p5);
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2, p3, p4, p5, p6);
        }
    }

    @Override
    public void fatal(
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
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }

    @Override
    public void fatal(
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
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }

    @Override
    public void fatal(
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
        if (log.isErrorEnabled(FATAL)) {
            log.error(FATAL, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public boolean isFatalEnabled() {
        return log.isErrorEnabled(FATAL);
    }
}
