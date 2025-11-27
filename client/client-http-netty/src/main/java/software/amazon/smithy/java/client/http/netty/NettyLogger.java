/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.Channel;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * An specialized {@link InternalLogger} to provide extra information to identify the channel that
 * creates the log events.
 */
public class NettyLogger {

    private final InternalLogger logger;

    protected NettyLogger(InternalLogger logger) {
        this.logger = logger;
    }

    /**
     * Creates a new Netty logger.
     *
     * @param clazz The class that creates the logs
     * @return The Netty logger
     */
    public static NettyLogger getLogger(Class<?> clazz) {
        return new NettyLogger(InternalLogger.getLogger(clazz));
    }

    /**
     * Logs a message for the given channel with the TRACE level.
     * <p>
     * `     * @param channel   channel
     *
     * @param message message
     */
    public void trace(Channel channel, String message) {
        if (logger.isTraceEnabled()) {
            logger.trace(addChannelIdToMessage(message, channel));
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel   channel
     * @param message   message format
     * @param throwable throwable
     */
    public void trace(Channel channel, String message, Throwable throwable) {
        if (logger.isTraceEnabled()) {
            logger.trace(addChannelIdToMessage(message, channel), throwable);
        }
    }

    /**
     * Logs a message for the given channel with the TRACE level.
     * Logs a message for the given channel with the TRACE level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     */
    public void trace(Channel channel, String message, Object p0) {
        if (logger.isTraceEnabled()) {
            logger.trace(addChannelIdToMessage(message, channel), p0);
        }
    }

    /**
     * Logs a message for the given channel with the TRACE level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     */
    public void trace(Channel channel, String message, Object p0, Object p1) {
        if (logger.isTraceEnabled()) {
            logger.trace(addChannelIdToMessage(message, channel), p0, p1);
        }
    }

    /**
     * Logs a message for the given channel with the TRACE level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     */
    public void trace(Channel channel, String message, Object p0, Object p1, Object p2) {
        if (logger.isTraceEnabled()) {
            logger.trace(addChannelIdToMessage(message, channel), p0, p1, p2);
        }
    }

    /**
     * Logs a message for the given channel with the TRACE level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     * @param p3      param three
     */
    public void trace(Channel channel, String message, Object p0, Object p1, Object p2, Object p3) {
        if (logger.isTraceEnabled()) {
            logger.trace(addChannelIdToMessage(message, channel), p0, p1, p2, p3);
        }
    }

    /**
     * Logs a message for the given channel with the TRACE level.
     *
     * @param channel channel
     * @param message message format
     * @param args    format args
     */
    public void trace(Channel channel, String message, Object... args) {
        if (logger.isTraceEnabled()) {
            logger.trace(addChannelIdToMessage(message, channel), args);
        }
    }

    /**
     * Logs a message for the given channel with the DEBUG level.
     *
     * @param channel channel
     * @param message message
     */
    public void debug(Channel channel, String message) {
        if (logger.isDebugEnabled()) {
            logger.debug(addChannelIdToMessage(message, channel));
        }
    }

    /**
     * Logs a message for the given channel with the DEBUG level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     */
    public void debug(Channel channel, String message, Object p0) {
        if (logger.isDebugEnabled()) {
            logger.debug(addChannelIdToMessage(message, channel), p0);
        }
    }

    /**
     * Logs a message for the given channel with the DEBUG level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     */
    public void debug(Channel channel, String message, Object p0, Object p1) {
        if (logger.isDebugEnabled()) {
            logger.debug(addChannelIdToMessage(message, channel), p0, p1);
        }
    }

    /**
     * Logs a message for the given channel with the DEBUG level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     */
    public void debug(Channel channel, String message, Object p0, Object p1, Object p2) {
        if (logger.isDebugEnabled()) {
            logger.debug(addChannelIdToMessage(message, channel), p0, p1, p2);
        }
    }

    /**
     * Logs a message for the given channel with the DEBUG level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     * @param p3      param three
     */
    public void debug(Channel channel, String message, Object p0, Object p1, Object p2, Object p3) {
        if (logger.isDebugEnabled()) {
            logger.debug(addChannelIdToMessage(message, channel), p0, p1, p2, p3);
        }
    }

    /**
     * Logs a message for the given channel with the DEBUG level.
     *
     * @param channel channel
     * @param message message format
     * @param args    format args
     */
    public void debug(Channel channel, String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(addChannelIdToMessage(message, channel), args);
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel channel
     * @param message message
     */
    public void info(Channel channel, String message) {
        if (logger.isInfoEnabled()) {
            logger.info(addChannelIdToMessage(message, channel));
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel   channel
     * @param message   message format
     * @param throwable throwable
     */
    public void info(Channel channel, String message, Throwable throwable) {
        if (logger.isInfoEnabled()) {
            logger.info(addChannelIdToMessage(message, channel), throwable);
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     */
    public void info(Channel channel, String message, Object p0) {
        if (logger.isInfoEnabled()) {
            logger.info(addChannelIdToMessage(message, channel), p0);
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     */
    public void info(Channel channel, String message, Object p0, Object p1) {
        if (logger.isInfoEnabled()) {
            logger.info(addChannelIdToMessage(message, channel), p0, p1);
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     */
    public void info(Channel channel, String message, Object p0, Object p1, Object p2) {
        if (logger.isInfoEnabled()) {
            logger.info(addChannelIdToMessage(message, channel), p0, p1, p2);
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     * @param p3      param three
     */
    public void info(Channel channel, String message, Object p0, Object p1, Object p2, Object p3) {
        if (logger.isInfoEnabled()) {
            logger.info(addChannelIdToMessage(message, channel), p0, p1, p2, p3);
        }
    }

    /**
     * Logs a message for the given channel with the INFO level.
     *
     * @param channel channel
     * @param message message format
     * @param args    format args
     */
    public void info(Channel channel, String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(addChannelIdToMessage(message, channel), args);
        }
    }

    /**
     * Logs a message for the given channel with the WARN level.
     *
     * @param channel channel
     * @param message message
     */
    public void warn(Channel channel, String message) {
        if (logger.isWarnEnabled()) {
            logger.warn(addChannelIdToMessage(message, channel));
        }
    }

    /**
     * Logs a message for the given channel with the WARN level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     */
    public void warn(Channel channel, String message, Object p0) {
        if (logger.isWarnEnabled()) {
            logger.warn(addChannelIdToMessage(message, channel), p0);
        }
    }

    /**
     * Logs a message for the given channel with the WARN level.
     *
     * @param channel   channel
     * @param message   message format
     * @param throwable throwable
     */
    public void warn(Channel channel, String message, Throwable throwable) {
        if (logger.isWarnEnabled()) {
            logger.warn(addChannelIdToMessage(message, channel), throwable);
        }
    }

    /**
     * Logs a message for the given channel with the WARN level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     */
    public void warn(Channel channel, String message, Object p0, Object p1) {
        if (logger.isWarnEnabled()) {
            logger.warn(addChannelIdToMessage(message, channel), p0, p1);
        }
    }

    /**
     * Logs a message for the given channel with the WARN level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     */
    public void warn(Channel channel, String message, Object p0, Object p1, Object p2) {
        if (logger.isWarnEnabled()) {
            logger.warn(addChannelIdToMessage(message, channel), p0, p1, p2);
        }
    }

    /**
     * Logs a message for the given channel with the WARN level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     * @param p3      param three
     */
    public void warn(Channel channel, String message, Object p0, Object p1, Object p2, Object p3) {
        if (logger.isWarnEnabled()) {
            logger.warn(addChannelIdToMessage(message, channel), p0, p1, p2, p3);
        }
    }

    /**
     * Logs a message for the given channel with the WARN level.
     *
     * @param channel channel
     * @param message message format
     * @param args    format args
     */
    public void warn(Channel channel, String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(addChannelIdToMessage(message, channel), args);
        }
    }

    /**
     * Logs a message for the given channel with the ERROR level.
     *
     * @param channel channel
     * @param message message
     */
    public void error(Channel channel, String message) {
        if (logger.isErrorEnabled()) {
            logger.error(addChannelIdToMessage(message, channel));
        }
    }

    /**
     * Logs a message for the given channel with the ERROR level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     */
    public void error(Channel channel, String message, Object p0) {
        if (logger.isErrorEnabled()) {
            logger.error(addChannelIdToMessage(message, channel), p0);
        }
    }

    /**
     * Logs a message for the given channel with the ERROR level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     */
    public void error(Channel channel, String message, Object p0, Object p1) {
        if (logger.isErrorEnabled()) {
            logger.error(addChannelIdToMessage(message, channel), p0, p1);
        }
    }

    /**
     * Logs a message for the given channel with the ERROR level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     */
    public void error(Channel channel, String message, Object p0, Object p1, Object p2) {
        if (logger.isErrorEnabled()) {
            logger.error(addChannelIdToMessage(message, channel), p0, p1, p2);
        }
    }

    /**
     * Logs a message for the given channel with the ERROR level.
     *
     * @param channel channel
     * @param message message format
     * @param p0      param zero
     * @param p1      param one
     * @param p2      param two
     * @param p3      param three
     */
    public void error(Channel channel, String message, Object p0, Object p1, Object p2, Object p3) {
        if (logger.isErrorEnabled()) {
            logger.error(addChannelIdToMessage(message, channel), p0, p1, p2, p3);
        }
    }

    /**
     * Logs a message for the given channel with the ERROR level.
     *
     * @param channel channel
     * @param message message format
     * @param args    format args
     */
    public void error(Channel channel, String message, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(addChannelIdToMessage(message, channel), args);
        }
    }

    protected String addChannelIdToMessage(String message, Channel channel) {
        if (channel == null) {
            return message;
        }
        String id;
        if (logger.isDebugEnabled()) {
            id = channel.toString();
        } else {
            id = channel.id().asShortText();
        }
        return String.format("[Channel: %s] %s", id, message);
    }
}
