package io.github.joke.percolate.spi.builtins.test

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.VarNames

/** Renders a single-input {@link EdgeCodegen} to its source string, feeding {@code inputName} for every input. */
final class Renders {

    private Renders() {
    }

    static String edge(final EdgeCodegen codegen, final String inputName) {
        codegen.render(new VarNames() {}, new IncomingValues() {
            CodeBlock single() { CodeBlock.of(inputName) }

            @SuppressWarnings('UnusedMethodParameter')
            CodeBlock byGroupPosition(final int idx) { CodeBlock.of(inputName) }

            @SuppressWarnings('UnusedMethodParameter')
            CodeBlock byName(final String slotName) { CodeBlock.of(inputName) }
        }).toString()
    }
}
