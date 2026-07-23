package io.github.joke.percolate.reactorblocking.test

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues

/**
 * A single-port {@link IncomingValues} fake for driving an {@link io.github.joke.percolate.spi.OperationCodegen}'s
 * {@code render} directly in a unit spec — mirroring the {@code singleInput} helper already established in
 * {@code strategies-builtin}'s {@code NullnessCrossingSpec}. Every blocking bridge strategy in this module renders
 * from exactly one incoming port ({@code inputs.single()}), so only that method needs a real implementation.
 */
final class Codegens {

    private Codegens() {
    }

    static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
