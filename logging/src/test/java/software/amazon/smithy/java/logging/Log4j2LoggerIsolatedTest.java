/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

public class Log4j2LoggerIsolatedTest extends LoggerTestBase {

    private static final StringWriter WRITER = new StringWriter();
    private static WriterAppender APPENDER;
    private static volatile boolean warnEnabled = true;

    @BeforeAll
    public static void setupLoggers() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration cfg = ctx.getConfiguration();
        cfg.getLoggerConfig("").setLevel(org.apache.logging.log4j.Level.TRACE);
        APPENDER = WriterAppender.newBuilder()
                .setTarget(WRITER)
                .setFilter(new AbstractFilter() {
                    @Override
                    public Result filter(LogEvent event) {
                        if (event.getLevel() == Level.WARN) {
                            return warnEnabled ? Result.NEUTRAL : Result.DENY;
                        }
                        return Result.NEUTRAL;
                    }
                })
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

    @Override
    protected List<String> getLogLines() {
        return Arrays.asList(WRITER.toString().split(System.lineSeparator()));
    }

    @Override
    protected void disableWarnLevel() {
        warnEnabled = false;
    }

    @Override
    protected void enableWarnLevel() {
        warnEnabled = true;
    }

}
