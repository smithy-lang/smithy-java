/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;


final class JdkSystemLogger implements InternalLogger {


    static final class Factory implements InternalLogger.Factory {

        @Override
        public InternalLogger getLogger(String name) {
            return new JdkSystemLogger(System.getLogger(name));
        }
    }

    private final System.Logger log;

    JdkSystemLogger(System.Logger log) {
        this.log = log;
    }

    private boolean isEnabled(Level level) {
        return switch (level) {
            case TRACE -> log.isLoggable(System.Logger.Level.TRACE);
            case DEBUG -> log.isLoggable(System.Logger.Level.DEBUG);
            case INFO -> log.isLoggable(System.Logger.Level.INFO);
            case WARN -> log.isLoggable(System.Logger.Level.WARNING);
            case ERROR, FATAL -> log.isLoggable(System.Logger.Level.ERROR);
        };
    }

    private void internalLog(Level level, String message, Throwable throwable) {
        switch (level) {
            case TRACE -> log.log(System.Logger.Level.TRACE, message, throwable);
            case DEBUG -> log.log(System.Logger.Level.DEBUG, message, throwable);
            case INFO -> log.log(System.Logger.Level.INFO, message, throwable);
            case WARN -> log.log(System.Logger.Level.WARNING, message, throwable);
            case ERROR -> log.log(System.Logger.Level.ERROR, message, throwable);
            case FATAL -> log.log(System.Logger.Level.ERROR, "[FATAL] " + message, throwable);
        }
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
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, params),
                    t
                );
            } else {
                internalLog(Level.TRACE, ParameterFormatter.format(message, params), null);
            }
        }
    }

    @Override
    public void trace(String message, Throwable throwable) {
        if (isEnabled(Level.TRACE)) {
            internalLog(Level.TRACE, message, throwable);
        }
    }

    @Override
    public void trace(String message, Object p0) {
        if (isEnabled(Level.TRACE)) {
            internalLog(Level.TRACE, ParameterFormatter.format(message, new Object[]{p0}), null);
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1) {
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                internalLog(Level.TRACE, ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                internalLog(Level.TRACE, ParameterFormatter.format(message, new Object[]{p0, p1}), null);
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2) {
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1}),
                    t
                );
            } else {
                internalLog(Level.TRACE, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), null);
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2}),
                    t
                );
            } else {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    null
                );
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    t
                );
            } else {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    null
                );
            }
        }

    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    t
                );
            } else {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    null
                );
            }
        }
    }

    @Override
    public void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.TRACE)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                internalLog(
                    Level.TRACE,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void debug(String message, Object... params) {
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, params),
                    t
                );
            } else {
                internalLog(Level.DEBUG, ParameterFormatter.format(message, params), null);
            }
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (isEnabled(Level.DEBUG)) {
            internalLog(Level.DEBUG, message, throwable);
        }
    }

    @Override
    public void debug(String message, Object p0) {
        if (isEnabled(Level.DEBUG)) {
            internalLog(Level.DEBUG, ParameterFormatter.format(message, new Object[]{p0}), null);
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1) {
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                internalLog(Level.DEBUG, ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                internalLog(Level.DEBUG, ParameterFormatter.format(message, new Object[]{p0, p1}), null);
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2) {
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1}),
                    t
                );
            } else {
                internalLog(Level.DEBUG, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), null);
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2}),
                    t
                );
            } else {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    null
                );
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    t
                );
            } else {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    null
                );
            }
        }

    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    t
                );
            } else {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    null
                );
            }
        }
    }

    @Override
    public void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.DEBUG)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                internalLog(
                    Level.DEBUG,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void info(String message, Object... params) {
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, params),
                    t
                );
            } else {
                internalLog(Level.INFO, ParameterFormatter.format(message, params), null);
            }
        }
    }

    @Override
    public void info(String message, Throwable throwable) {
        if (isEnabled(Level.INFO)) {
            internalLog(Level.INFO, message, throwable);
        }
    }

    @Override
    public void info(String message, Object p0) {
        if (isEnabled(Level.INFO)) {
            internalLog(Level.INFO, ParameterFormatter.format(message, new Object[]{p0}), null);
        }
    }

    @Override
    public void info(String message, Object p0, Object p1) {
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                internalLog(Level.INFO, ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                internalLog(Level.INFO, ParameterFormatter.format(message, new Object[]{p0, p1}), null);
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2) {
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1}),
                    t
                );
            } else {
                internalLog(Level.INFO, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), null);
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2}),
                    t
                );
            } else {
                internalLog(Level.INFO, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), null);
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    t
                );
            } else {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    null
                );
            }
        }

    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    t
                );
            } else {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    null
                );
            }
        }
    }

    @Override
    public void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.INFO)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                internalLog(
                    Level.INFO,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void warn(String message, Object... params) {
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, params),
                    t
                );
            } else {
                internalLog(Level.WARN, ParameterFormatter.format(message, params), null);
            }
        }
    }

    @Override
    public void warn(String message, Throwable throwable) {
        if (isEnabled(Level.WARN)) {
            internalLog(Level.WARN, message, throwable);
        }
    }

    @Override
    public void warn(String message, Object p0) {
        if (isEnabled(Level.WARN)) {
            internalLog(Level.WARN, ParameterFormatter.format(message, new Object[]{p0}), null);
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1) {
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                internalLog(Level.WARN, ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                internalLog(Level.WARN, ParameterFormatter.format(message, new Object[]{p0, p1}), null);
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2) {
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1}),
                    t
                );
            } else {
                internalLog(Level.WARN, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), null);
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2}),
                    t
                );
            } else {
                internalLog(Level.WARN, ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}), null);
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    t
                );
            } else {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    null
                );
            }
        }

    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    t
                );
            } else {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    null
                );
            }
        }
    }

    @Override
    public void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.WARN)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                internalLog(
                    Level.WARN,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void error(String message, Object... params) {
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, params),
                    t
                );
            } else {
                internalLog(Level.ERROR, ParameterFormatter.format(message, params), null);
            }
        }
    }

    @Override
    public void error(String message, Throwable throwable) {
        if (isEnabled(Level.ERROR)) {
            internalLog(Level.ERROR, message, throwable);
        }
    }

    @Override
    public void error(String message, Object p0) {
        if (isEnabled(Level.ERROR)) {
            internalLog(Level.ERROR, ParameterFormatter.format(message, new Object[]{p0}), null);
        }
    }

    @Override
    public void error(String message, Object p0, Object p1) {
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                internalLog(Level.ERROR, ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                internalLog(Level.ERROR, ParameterFormatter.format(message, new Object[]{p0, p1}), null);
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2) {
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1}),
                    t
                );
            } else {
                internalLog(Level.ERROR, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), null);
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2}),
                    t
                );
            } else {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    null
                );
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    t
                );
            } else {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    null
                );
            }
        }

    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    t
                );
            } else {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    null
                );
            }
        }
    }

    @Override
    public void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.ERROR)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                internalLog(
                    Level.ERROR,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public void fatal(String message, Object... params) {
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == params.length - 1
                && params[params.length - 1] instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, params),
                    t
                );
            } else {
                internalLog(Level.FATAL, ParameterFormatter.format(message, params), null);
            }
        }
    }

    @Override
    public void fatal(String message, Throwable throwable) {
        if (isEnabled(Level.FATAL)) {
            internalLog(Level.FATAL, message, throwable);
        }
    }

    @Override
    public void fatal(String message, Object p0) {
        if (isEnabled(Level.FATAL)) {
            internalLog(Level.FATAL, ParameterFormatter.format(message, new Object[]{p0}), null);
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1) {
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 1 && p1 instanceof Throwable t) {
                internalLog(Level.FATAL, ParameterFormatter.format(message, new Object[]{p0}), t);
            } else {
                internalLog(Level.FATAL, ParameterFormatter.format(message, new Object[]{p0, p1}), null);
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2) {
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 2 && p2 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1}),
                    t
                );
            } else {
                internalLog(Level.FATAL, ParameterFormatter.format(message, new Object[]{p0, p1, p2}), null);
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3) {
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 3 && p3 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2}),
                    t
                );
            } else {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    null
                );
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 4 && p4 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3}),
                    t
                );
            } else {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    null
                );
            }
        }

    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 5 && p5 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4}),
                    t
                );
            } else {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    null
                );
            }
        }
    }

    @Override
    public void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 6 && p6 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5}),
                    t
                );
            } else {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6}),
                    null
                );
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
        if (isEnabled(Level.FATAL)) {
            if (ParameterFormatter.countArgumentPlaceholders(message) == 9 && p9 instanceof Throwable t) {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    t
                );
            } else {
                internalLog(
                    Level.FATAL,
                    ParameterFormatter.format(message, new Object[]{p0, p1, p2, p3, p4, p5, p6, p7, p8, p9}),
                    null
                );
            }
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return isEnabled(Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return isEnabled(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    @Override
    public boolean isFatalEnabled() {
        return isEnabled(Level.FATAL);
    }
}
