package io.github.joke.percolate.processor.internal.stages.generate;

import lombok.Value;

/**
 * How hoisted local declarations are rendered (the {@code percolate.locals.*} options): whether to prefix
 * {@code final} and whether to use {@code var} in place of the explicit type. Neither flag affects which Values
 * hoist — only the declaration syntax.
 */
@Value
class LocalStyle {
    boolean makeFinal;
    boolean useVar;
}
