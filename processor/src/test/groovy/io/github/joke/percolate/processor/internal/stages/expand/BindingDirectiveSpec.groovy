package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.model.MappingDirective
import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link BindingDirective} seam, unit-tested directly: the per-binding {@link io.github.joke.percolate.spi.Directive}
 * the demand context carries into a strategy, derived from a discovered {@code @Map} {@link MappingDirective}. A pure
 * adapter — it splits the dotted source path into segments and lifts the {@code constant}/{@code defaultValue} members
 * (already decided present/absent against the {@code Map.UNSET} sentinel by discovery) into {@link java.util.Optional}.
 */
@Tag('unit')
class BindingDirectiveSpec extends Specification {

    def 'splits a dotted source path into its segments'() {
        expect:
        BindingDirective.from(directive('person.address.street', null, null)).sourcePath() ==
                ['person', 'address', 'street']
    }

    def 'a single-segment source yields a one-element path'() {
        expect:
        BindingDirective.from(directive('name', null, null)).sourcePath() == ['name']
    }

    def 'an absent or empty source yields an empty path'() {
        expect:
        BindingDirective.from(directive(source, null, null)).sourcePath() == []

        where:
        source << [null, '']
    }

    def 'constant and defaultValue are carried as present Optionals when set (empty string is present)'() {
        when:
        def directive = BindingDirective.from(directive(null, '', 'fallback'))

        then:
        directive.constant() == Optional.of('')
        directive.defaultValue() == Optional.of('fallback')
        directive.sourcePath() == []
    }

    def 'constant and defaultValue are empty Optionals when absent'() {
        when:
        def directive = BindingDirective.from(directive('a', null, null))

        then:
        directive.constant() == Optional.empty()
        directive.defaultValue() == Optional.empty()
    }

    private static MappingDirective directive(final String source, final String constant, final String defaultValue) {
        new MappingDirective('target', source, constant, defaultValue, null, null, null, null, null)
    }
}
