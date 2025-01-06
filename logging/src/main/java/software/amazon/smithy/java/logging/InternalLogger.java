/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

public sealed interface InternalLogger permits Log4j2Logger, Slf4jLogger, JclLogger, JdkSystemLogger {

    sealed interface Factory permits Log4j2Logger.Factory, Slf4jLogger.Factory, JclLogger.Factory,
            JdkSystemLogger.Factory {
        InternalLogger getLogger(String name);
    }

    Factory FACTORY = findLoggingFactory();

    private static Factory findLoggingFactory() {
        Factory factory = getLog4J2Factory();
        if (factory == null) {
            factory = getSlf4jFactory();
        }
        if (factory == null) {
            factory = getJclFactory();
        }
        if (factory != null) {
            return factory;
        }
        return new JdkSystemLogger.Factory();
    }

    private static Factory getLog4J2Factory() {
        try {
            var f = new Log4j2Logger.Factory();
            f.getLogger(InternalLogger.class.getName());
            return f;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Factory getSlf4jFactory() {
        try {
            var f = new Slf4jLogger.Factory();
            f.getLogger(InternalLogger.class.getName());
            return f;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Factory getJclFactory() {
        try {
            var f = new JclLogger.Factory();
            f.getLogger(InternalLogger.class.getName());
            return f;
        } catch (Throwable e) {
            return null;
        }
    }

    static InternalLogger getLogger(Class<?> clazz) {
        return FACTORY.getLogger(clazz.getName());
    }

    static InternalLogger getLogger(String name) {
        return FACTORY.getLogger(name);
    }

    enum Level {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        FATAL
    }

    void log(Level level, String message, Object... params);

    void log(Level level, String message, Throwable throwable);

    void log(Level level, String message, Object p0);

    void log(Level level, String message, Object p0, Object p1);

    void log(Level level, String message, Object p0, Object p1, Object p2);

    void log(Level level, String message, Object p0, Object p1, Object p2, Object p3);

    void log(Level level, String message, Object p0, Object p1, Object p2, Object p3, Object p4);

    void log(Level level, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

    void log(
            Level level,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6
    );

    void log(
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
    );

    void log(
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
    );

    void log(
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
    );

    void trace(String message, Object... params);

    void trace(String message, Throwable throwable);

    void trace(String message, Object p0);

    void trace(String message, Object p0, Object p1);

    void trace(String message, Object p0, Object p1, Object p2);

    void trace(String message, Object p0, Object p1, Object p2, Object p3);

    void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4);

    void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

    void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

    void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

    void trace(
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
    );

    void trace(
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
    );

    void debug(String message, Object... params);

    void debug(String message, Throwable throwable);

    void debug(String message, Object p0);

    void debug(String message, Object p0, Object p1);

    void debug(String message, Object p0, Object p1, Object p2);

    void debug(String message, Object p0, Object p1, Object p2, Object p3);

    void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4);

    void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

    void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

    void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

    void debug(
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
    );

    void debug(
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
    );

    void info(String message, Object... params);

    void info(String message, Throwable throwable);

    void info(String message, Object p0);

    void info(String message, Object p0, Object p1);

    void info(String message, Object p0, Object p1, Object p2);

    void info(String message, Object p0, Object p1, Object p2, Object p3);

    void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4);

    void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

    void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

    void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

    void info(
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
    );

    void info(
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
    );

    void warn(String message, Object... params);

    void warn(String message, Throwable throwable);

    void warn(String message, Object p0);

    void warn(String message, Object p0, Object p1);

    void warn(String message, Object p0, Object p1, Object p2);

    void warn(String message, Object p0, Object p1, Object p2, Object p3);

    void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4);

    void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

    void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

    void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

    void warn(
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
    );

    void warn(
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
    );

    void error(String message, Object... params);

    void error(String message, Throwable throwable);

    void error(String message, Object p0);

    void error(String message, Object p0, Object p1);

    void error(String message, Object p0, Object p1, Object p2);

    void error(String message, Object p0, Object p1, Object p2, Object p3);

    void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4);

    void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

    void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

    void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

    void error(
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
    );

    void error(
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
    );

    void fatal(String message, Object... params);

    void fatal(String message, Throwable throwable);

    void fatal(String message, Object p0);

    void fatal(String message, Object p0, Object p1);

    void fatal(String message, Object p0, Object p1, Object p2);

    void fatal(String message, Object p0, Object p1, Object p2, Object p3);

    void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4);

    void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

    void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

    void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

    void fatal(
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
    );

    void fatal(
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
    );

    boolean isTraceEnabled();

    boolean isDebugEnabled();

    boolean isInfoEnabled();

    boolean isWarnEnabled();

    boolean isErrorEnabled();

    boolean isFatalEnabled();
}
