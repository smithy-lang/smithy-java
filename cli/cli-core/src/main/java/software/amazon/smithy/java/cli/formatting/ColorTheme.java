/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.formatting;

/**
 * Standardizes on colors across commands.
 */
public interface ColorTheme {

    default Style rootCommandUsage() {
        return Style.of(Style.BRIGHT_WHITE, Style.UNDERLINE);
    }

    default Style subcommandUsage() {
        return Style.of(Style.BRIGHT_WHITE, Style.UNDERLINE);
    }

    default Style deprecated() {
        return Style.of(Style.BG_YELLOW, Style.BLACK);
    }

    default Style muted() {
        return Style.of(Style.BRIGHT_BLACK);
    }

    default Style literal() {
        return Style.of(Style.CYAN);
    }

    default Style errorTitle() {
        return Style.of(Style.BG_RED, Style.BLACK);
    }

    default Style errorDescription() {
        return Style.of(Style.RED);
    }

    default Style warningTitle() {
        return Style.of(Style.BG_YELLOW, Style.BLACK);
    }

    default Style warningDescription() {
        return Style.of(Style.YELLOW);
    }

    default Style title() {
        return Style.of(Style.BOLD, Style.BRIGHT_BLACK);
    }

    default Style description() {
        return Style.of(Style.WHITE);
    }

    default Style success() {
        return Style.of(Style.GREEN);
    }

    default Style commandTitle() {
        return Style.of(Style.BOLD, Style.BRIGHT_WHITE);
    }

    static ColorTheme defaultTheme() {
        return new ColorTheme() {};
    }
}
