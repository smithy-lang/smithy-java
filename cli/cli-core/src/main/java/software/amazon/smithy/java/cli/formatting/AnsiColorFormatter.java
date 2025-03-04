/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.formatting;

import java.io.IOException;
import java.io.UncheckedIOException;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Styles text using ANSI color codes.
 */
@SmithyInternalApi
public enum AnsiColorFormatter implements ColorFormatter {

    /**
     * Does not write any color.
     */
    NO_COLOR {
        @Override
        public boolean isColorEnabled() {
            return false;
        }

        @Override
        public void startStyle(Appendable appendable, Style... style) {}

        @Override
        public void endStyle(Appendable appendable) {}
    },

    /**
     * Writes with ANSI colors.
     */
    FORCE_COLOR {
        @Override
        public boolean isColorEnabled() {
            return true;
        }

        @Override
        public void startStyle(Appendable appendable, Style... styles) {
            if (styles.length > 0) {
                try {
                    appendable.append("\033[");
                    boolean isAfterFirst = false;
                    for (Style style : styles) {
                        if (isAfterFirst) {
                            appendable.append(';');
                        }
                        appendable.append(style.getAnsiColorCode());
                        isAfterFirst = true;
                    }
                    appendable.append('m');
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public void endStyle(Appendable appendable) {
            try {
                appendable.append("\033[0m");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    },

    /**
     * Writes using ANSI colors if it detects that the environment supports color.
     */
    AUTO {
        private final AnsiColorFormatter delegate = AnsiColorFormatter.detect();

        @Override
        public String style(String text, Style... styles) {
            return delegate.style(text, styles);
        }

        @Override
        public void println(Appendable appendable, String text, Style... styles) {
            delegate.println(appendable, text, styles);
        }

        @Override
        public void style(Appendable appendable, String text, Style... styles) {
            delegate.style(appendable, text, styles);
        }

        @Override
        public boolean isColorEnabled() {
            return delegate.isColorEnabled();
        }

        @Override
        public void startStyle(Appendable appendable, Style... style) {
            delegate.startStyle(appendable, style);
        }

        @Override
        public void endStyle(Appendable appendable) {
            delegate.endStyle(appendable);
        }
    };

    /**
     * Detects if ANSI colors are supported and returns the appropriate Ansi enum variant.
     *
     * <p>This method differs from using the {@link AnsiColorFormatter#AUTO} variant directly because it will
     * detect any changes to the environment that might enable or disable colors.
     *
     * @return Returns the detected ANSI color enum variant.
     */
    public static AnsiColorFormatter detect() {
        return isAnsiEnabled() ? FORCE_COLOR : NO_COLOR;
    }

    private static boolean isAnsiEnabled() {
        // If Windows is detected, then don't use colors.
        return !System.getProperty("os.name").contains("win");
    }
}
