/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static software.amazon.smithy.java.logging.InternalLogger.Level.ERROR;
import static software.amazon.smithy.java.logging.InternalLogger.Level.FATAL;
import static software.amazon.smithy.java.logging.InternalLogger.Level.WARN;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class LoggerTestBase {

    protected abstract List<String> getLogLines();

    protected String getLevelString(InternalLogger.Level level) {
        return level.name();
    }

    protected abstract void disableWarnLevel();

    protected abstract void enableWarnLevel();

    @AfterEach
    public void tear() {
        enableWarnLevel();
    }

    @Test
    void smokeTest() {
        InternalLogger logger = InternalLogger.getLogger(getClass());
        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = getLogLines();
        assertThat(lines).hasSizeGreaterThan(5);
        assertThat(lines.get(0)).startsWith(getLevelString(ERROR) + " test1");
        assertThat(lines.get(1)).startsWith(getLevelString(WARN) + " test2: abc");
        assertThat(lines.get(2)).startsWith(getLevelString(FATAL) + " a: test3: b c");
        assertThat(lines.get(3)).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines.get(4))
                .contains("at software.amazon.smithy.java.logging.LoggerTestBase.smokeTest(LoggerTestBase.java:");
    }

    @Test
    void levelTest() {
        disableWarnLevel();
        InternalLogger logger = InternalLogger.getLogger(getClass());
        logger.error("test1");
        logger.warn("test2: {}", "abc");
        logger.fatal("{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = getLogLines();
        assertThat(lines).hasSizeGreaterThan(4);
        assertThat(lines.get(0)).startsWith(getLevelString(ERROR) + " test1");
        assertThat(lines.get(1)).startsWith(getLevelString(FATAL) + " a: test3: b c");
        assertThat(lines.get(2)).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines.get(3))
                .contains("at software.amazon.smithy.java.logging.LoggerTestBase.levelTest(LoggerTestBase.java:");
    }

    @Test
    void dynamicLevelTest() {
        InternalLogger logger = InternalLogger.getLogger(getClass());
        disableWarnLevel();
        logger.log(ERROR, "test1");
        logger.log(InternalLogger.Level.WARN, "test2: {}", "abc");
        logger.log(InternalLogger.Level.FATAL, "{}: test3: {} {}", "a", "b", "c", new NullPointerException("BANG!"));

        var lines = getLogLines();
        assertThat(lines).hasSizeGreaterThan(4);
        assertThat(lines.get(0)).startsWith(getLevelString(ERROR) + " test1");
        assertThat(lines.get(1)).startsWith(getLevelString(FATAL) + " a: test3: b c");
        assertThat(lines.get(2)).startsWith("java.lang.NullPointerException: BANG!");
        assertThat(lines.get(3)).contains(
                "at software.amazon.smithy.java.logging.LoggerTestBase.dynamicLevelTest(LoggerTestBase.java:");
    }

    @ParameterizedTest
    @MethodSource("multipleParams")
    void testMultipleParams(
            InternalLogger.Level level,
            int numberOfParams,
            boolean isException,
            boolean invokeDynamicLevelMethod
    ) throws InvocationTargetException, IllegalAccessException {
        InternalLogger logger = InternalLogger.getLogger(getClass());
        Method loggerMethod = findLogMethod(level, numberOfParams, invokeDynamicLevelMethod);
        loggerMethod.invoke(logger,
                prepareMethodArguments(level, numberOfParams, isException, invokeDynamicLevelMethod));
        var lines = getLogLines();
        var expectedMessageBuilder = new StringBuilder(getLevelString(level)).append(" ");
        for (int i = 1; i <= numberOfParams; i++) {
            if (isException && i == numberOfParams) {
                break;
            } else {
                expectedMessageBuilder.append("Param:").append(i).append(" ");
            }
        }
        String expectedMessage = expectedMessageBuilder.toString().trim();
        if (isException) {
            assertThat(lines).hasSizeGreaterThan(3);
            assertThat(lines.get(0)).isEqualTo(expectedMessage);
            assertThat(lines.get(1)).startsWith("java.lang.IllegalArgumentException: lol");
        } else {
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0)).isEqualTo(expectedMessage);
        }
    }

    private static List<Arguments> multipleParams() {
        List<Arguments> params = new ArrayList<>();
        for (InternalLogger.Level level : InternalLogger.Level.values()) {
            for (int i = 2; i <= 11; i++) {
                for (boolean isException : new boolean[] {true, false}) {
                    for (boolean invokeDynamicLevelMethod : new boolean[] {true, false}) {
                        params.add(arguments(level, i, isException, invokeDynamicLevelMethod));
                    }
                }
            }
        }
        return params;
    }

    private Method findLogMethod(InternalLogger.Level level, int paramCount, boolean invokeDynamicLevelMethod) {
        var methodName = invokeDynamicLevelMethod ? "log" : level.name().toLowerCase();
        try {
            if (paramCount > 10) {
                if (invokeDynamicLevelMethod) {
                    return InternalLogger.class
                            .getMethod(methodName, InternalLogger.Level.class, String.class, Object[].class);
                } else {
                    return InternalLogger.class.getMethod(methodName, String.class, Object[].class);
                }
            }

            Class<?>[] paramTypes;
            paramTypes = new Class<?>[paramCount + 2];
            Arrays.fill(paramTypes, Object.class);
            paramTypes[0] = InternalLogger.Level.class;
            paramTypes[1] = String.class;
            if (!invokeDynamicLevelMethod) {
                paramTypes = Arrays.copyOfRange(paramTypes, 1, paramTypes.length);
            }
            return InternalLogger.class.getMethod(methodName, paramTypes);

        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Object[] prepareMethodArguments(
            InternalLogger.Level level,
            int paramCount,
            boolean lastException,
            boolean invokeDynamicLevelMethod
    ) {
        Object[] args;
        if (paramCount > 10) {
            args = new Object[3];
            Object[] params = new Object[paramCount];
            for (int i = 0; i < paramCount; i++) {
                params[i] = i + 1;
            }
            if (lastException) {
                params[paramCount - 1] = new IllegalArgumentException("lol");
            }
            args[2] = params;
        } else {
            args = new Object[paramCount + 2];
            for (int i = 0; i < paramCount; i++) {
                args[2 + i] = i + 1;
            }
            if (lastException) {
                args[paramCount + 1] = new IllegalArgumentException("lol");
            }
        }
        args[0] = level;
        args[1] = "Param:{} ".repeat(lastException ? paramCount - 1 : paramCount).trim();
        if (!invokeDynamicLevelMethod) {
            return Arrays.copyOfRange(args, 1, args.length);
        }
        return args;
    }
}
