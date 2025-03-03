/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import software.amazon.smithy.java.cli.arguments.ArgumentReceiver;
import software.amazon.smithy.java.cli.arguments.Arguments;
import software.amazon.smithy.java.cli.formatting.ColorBuffer;
import software.amazon.smithy.java.cli.formatting.ColorFormatter;
import software.amazon.smithy.java.cli.formatting.ColorTheme;
import software.amazon.smithy.utils.CaseUtils;

public class CliUtils {
    private CliUtils() {}

    // TODO: add to case utils
    public static String kebabCase(String string) {
        return CaseUtils.toSnakeCase(string).replace('_', '-');
    }

    public static void printFlags(ColorBuffer buffer, ColorFormatter formatter, ColorTheme theme, Arguments args) {
        buffer.println("Flags:", theme.title());
        var shortOffset = shortFlagOffset(args);
        var longestFlag = longestFlagLength(args);
        for (ArgumentReceiver receiver : args.getReceivers()) {
            for (var flagEntry : receiver.flags().entrySet()) {
                var flag = flagEntry.getKey();
                var template = flag.shortFlag() == null
                        ? "    %" + shortOffset + "s   %-" + longestFlag + "s  %s"
                        : "    %" + shortOffset + "s, %-" + longestFlag + "s %s";
                buffer.println(
                        String.format(
                                template,
                                formatter.style(flag.shortFlag() != null ? flag.shortFlag() : "", theme.literal()),
                                formatter.style(flag.longFlag(), theme.literal()),
                                formatter.style(flagEntry.getValue(), theme.description())));
            }
        }
    }

    private static int shortFlagOffset(Arguments args) {
        int longestFlag = 0;
        for (ArgumentReceiver receiver : args.getReceivers()) {
            for (var flag : receiver.flags().keySet()) {
                if (flag.shortFlag() != null && flag.shortFlag().length() + 8 > longestFlag) {
                    longestFlag = flag.shortFlag().length() + 8;
                }
            }
        }
        return longestFlag;
    }

    private static int longestFlagLength(Arguments args) {
        int longestFlag = 0;
        for (ArgumentReceiver receiver : args.getReceivers()) {
            for (var flag : receiver.flags().keySet()) {
                if (flag.longFlag().length() + 10 > longestFlag) {
                    longestFlag = flag.longFlag().length() + 10;
                }
            }
        }
        return longestFlag;
    }
}
