/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

final class JclLogger implements InternalLogger {


    static final class Factory implements InternalLogger.Factory {

        @Override
        public InternalLogger getLogger(String name) {
            return new JclLogger(LogFactory.getLog(name));
        }
    }


    private final Log log;

    JclLogger(Log log) {
        this.log = log;
    }


    private void internalLog(Level level, String message, Throwable throwable) {
        switch (level) {
            case TRACE -> log.trace(message, throwable);
            case DEBUG -> log.debug(message, throwable);
            case INFO -> log.info(message, throwable);
            case WARN -> log.warn(message, throwable);
            case ERROR -> log.error(message, throwable);
            case FATAL -> log.fatal(message, throwable);
        }
    }

    private boolean isEnabled(Level level) {
        return switch (level) {
            case TRACE -> log.isTraceEnabled();
            case DEBUG -> log.isDebugEnabled();
            case INFO -> log.isInfoEnabled();
            case WARN -> log.isWarnEnabled();
            case ERROR -> log.isErrorEnabled();
            case FATAL -> log.isFatalEnabled();
        };
    }


    @Override
    public void log(Level level, String message, Object... params) {
        if (isEnabled(level)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                internalLog(level, ParameterFormatter.format(message, params), t);
            } else {
                internalLog(level, ParameterFormatter.format(message, params), null);
            }
        }
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        if (isEnabled(level)) {
            internalLog(level, message, throwable);
        }
    }

    @Override
    public void log(Level level, String message, Object p0) {
        if (isEnabled(level)) {
            internalLog(level, ParameterFormatter.format(message, new Object[]{p0}), null);
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1) {
        if (isEnabled(level)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1}), null);
            }
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2) {
        if (isEnabled(level)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1}), t);
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), null);
            }
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(level)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), t);
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), null);
            }
        }
    }

    @Override
    public void log(Level level, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(level)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), t);
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}), null);
            }
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
        Object p5
    ) {
        if (isEnabled(level)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                internalLog(
                    level,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    t
                );
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), null);
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    level,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}), null);
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    level,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}), null);
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    level,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(level, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}), null);
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                internalLog(
                    level,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                internalLog(
                    level,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void trace(String message, Object... params) {
        if (log.isTraceEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, params), t);
            } else {
                log.trace(ParameterFormatter.format(message, params));
            }
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
            log.trace(ParameterFormatter.format(message, new Object[]{p0}));
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1) {
        if (log.isTraceEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1}));
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2) {
        if (log.isTraceEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2}));
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isTraceEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}));
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isTraceEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}));
            }
        }

    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isTraceEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}));
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isTraceEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.trace(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                log.trace(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                log.trace(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void debug(String message, Object... params) {
        if (log.isDebugEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, params), t);
            } else {
                log.debug(ParameterFormatter.format(message, params));
            }
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
            log.debug(ParameterFormatter.format(message, new Object[]{p0}));
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1) {
        if (log.isDebugEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1}));
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2) {
        if (log.isDebugEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2}));
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isDebugEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}));
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isDebugEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}));
            }
        }

    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isDebugEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}));
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isDebugEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.debug(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                log.debug(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                log.debug(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void info(String message, Object... params) {
        if (log.isInfoEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, params), t);
            } else {
                log.info(ParameterFormatter.format(message, params));
            }
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
            log.info(ParameterFormatter.format(message, new Object[]{p0}));
        }
    }

    @Override
    public void info(String message, Object p0, Object p1) {
        if (log.isInfoEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1}));
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2) {
        if (log.isInfoEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2}));
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isInfoEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}));
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isInfoEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}));
            }
        }

    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isInfoEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}));
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isInfoEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.info(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                log.info(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                log.info(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void warn(String message, Object... params) {
        if (log.isWarnEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, params), t);
            } else {
                log.warn(ParameterFormatter.format(message, params));
            }
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
            log.warn(ParameterFormatter.format(message, new Object[]{p0}));
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1) {
        if (log.isWarnEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1}));
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2) {
        if (log.isWarnEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2}));
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isWarnEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}));
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isWarnEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}));
            }
        }

    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isWarnEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}));
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isWarnEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.warn(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                log.warn(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                log.warn(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9})
                );
            }
        }
    }

    @Override
    public void error(String message, Object... params) {
        if (log.isErrorEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, params), t);
            } else {
                log.error(ParameterFormatter.format(message, params));
            }
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
            log.error(ParameterFormatter.format(message, new Object[]{p0}));
        }
    }

    @Override
    public void error(String message, Object p0, Object p1) {
        if (log.isErrorEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1}));
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2) {
        if (log.isErrorEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2}));
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isErrorEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}));
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isErrorEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}));
            }
        }

    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isErrorEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}));
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isErrorEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.error(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                log.error(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                log.error(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void fatal(String message, Object... params) {
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, params), t);
            } else {
                log.fatal(ParameterFormatter.format(message, params));
            }
        }
    }

    @Override
    public void fatal(String message, Throwable throwable) {
        if (log.isFatalEnabled()) {
            log.fatal(message, throwable);
        }
    }

    @Override
    public void fatal(String message, Object p0) {
        if (log.isFatalEnabled()) {
            log.fatal(ParameterFormatter.format(message, new Object[]{p0}));
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1) {
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1}));
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2) {
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2}));
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3) {
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}));
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}));
            }
        }

    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}));
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}), t);
            } else {
                log.fatal(ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}));
            }
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
        if (log.isFatalEnabled()) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                log.fatal(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                log.fatal(
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9})
                );
            }
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
        return log.isFatalEnabled();
    }
}
