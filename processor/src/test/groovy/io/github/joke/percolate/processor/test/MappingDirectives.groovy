package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.Subjects

/**
 * Test-only factory for {@link MappingDirective} over the open {@link DirectiveInput} bag (design D4 of change
 * {@code decouple-engine-from-strategy-semantics}): {@code members} keys are among {@code source}, {@code
 * constant}, {@code defaultValue}, {@code format}, {@code zone} — present only when passed. Every subject is
 * {@link Subjects#none()}; specs that need to assert on a diagnostic's message/permanence never need a specific one.
 */
final class MappingDirectives {

    private MappingDirectives() {
    }

    static MappingDirective of(final String target, final Map<String, String> members = [:]) {
        def inputs = members.collect { key, value -> DirectiveInput.scalar(key as String, value as String, Subjects.none()) }
        new MappingDirective(target, Subjects.none(), inputs)
    }
}
