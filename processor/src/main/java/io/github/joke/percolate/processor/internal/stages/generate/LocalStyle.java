package io.github.joke.percolate.processor.internal.stages.generate;

import lombok.Value;

// How hoisted local declarations are rendered (the percolate.locals.* options): whether to prefix final and
// whether to use var in place of the explicit type. Neither flag affects which Values hoist — only the
// declaration syntax.
@Value
class LocalStyle {
    boolean makeFinal;
    boolean useVar;
}
