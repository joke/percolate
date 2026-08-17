package io.github.joke.percolate.spi;

import javax.lang.model.SourceVersion;

/**
 * The generated conversion switch form — the parsed {@code -Apercolate.switch.style} processor option, which a
 * {@link BodyCodegen} reads for itself through {@link ResolveCtx#option(String)} and parses: {@link #AUTO} defers to
 * the target {@link SourceVersion} (a modern arrow switch expression on Java 14+, else a classic switch statement),
 * {@link #CLASSIC} always renders the classic statement, {@link #ARROW} always renders the modern expression. Read
 * by a strategy's codegen alone — the engine reads and decides none of this.
 */
public enum SwitchStyle {
    AUTO,
    CLASSIC,
    ARROW
}
