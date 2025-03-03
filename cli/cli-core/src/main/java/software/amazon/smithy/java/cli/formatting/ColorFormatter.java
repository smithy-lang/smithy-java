/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.formatting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/**
 * Styles text using color codes.
 *
 * @see AnsiColorFormatter for the ANSI implementation.
 */
// TODO: Do we really need an interface for this one?
public sealed interface ColorFormatter permits AnsiColorFormatter, ColorFormatter.DelegatedFormatter {

    /**
     * Styles text using the given styles.
     *
     * @param text Text to style.
     * @param styles Styles to apply.
     * @return Returns the styled text.
     */
    // TODO: do we really need to have varargs here?
    default String style(String text, Style... styles) {
        if (!isColorEnabled()) {
            return text;
        } else {
            StringBuilder builder = new StringBuilder();
            style(builder, text, styles);
            return builder.toString();
        }
    }

    /**
     * Styles text using the given styles and writes it to an Appendable.
     *
     * @param appendable Where to write styled text.
     * @param text Text to write.
     * @param styles Styles to apply.
     */
    default void style(Appendable appendable, String text, Style... styles) {
        try {
            startStyle(appendable, styles);
            appendable.append(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (styles.length > 0) {
                endStyle(appendable);
            }
        }
    }

    /**
     * Print a styled line of text to the given {@code appendable}.
     *
     * @param appendable Where to write.
     * @param text Text to write.
     * @param styles Styles to apply.
     */
    default void println(Appendable appendable, String text, Style... styles) {
        style(appendable, text + System.lineSeparator(), styles);
    }

    /**
     * TODO: DOCS
     * @param appendable
     * @param throwable
     * @param styles
     */
    default void printException(Appendable appendable, Throwable throwable, Style... styles) {
        println(appendable, throwable.getMessage(), styles);
    }

    /**
     * @return Returns true if this formatter supports color output.
     */
    boolean isColorEnabled();

    void startStyle(Appendable appendable, Style... style);

    void endStyle(Appendable appendable);

    static ColorFormatter createDelegated(Supplier<ColorFormatter> delegateSupplier) {
        return new DelegatedFormatter(delegateSupplier);
    }

    final class DelegatedFormatter implements ColorFormatter {
        private final Supplier<ColorFormatter> delegateSupplier;

        public DelegatedFormatter(Supplier<ColorFormatter> delegateSupplier) {
            this.delegateSupplier = delegateSupplier;
        }

        @Override
        public String style(String text, Style... styles) {
            return delegateSupplier.get().style(text, styles);
        }

        @Override
        public void style(Appendable appendable, String text, Style... styles) {
            delegateSupplier.get().style(appendable, text, styles);
        }

        @Override
        public boolean isColorEnabled() {
            return delegateSupplier.get().isColorEnabled();
        }

        @Override
        public void println(Appendable appendable, String text, Style... styles) {
            delegateSupplier.get().println(appendable, text, styles);
        }

        @Override
        public void startStyle(Appendable appendable, Style... style) {
            delegateSupplier.get().startStyle(appendable, style);
        }

        @Override
        public void endStyle(Appendable appendable) {
            delegateSupplier.get().endStyle(appendable);
        }
    }
}
