package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.model.EnumOverrideDirective
import io.github.joke.percolate.processor.model.MappingDirective
import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link BindingDirective} seam, unit-tested directly: the per-binding {@link io.github.joke.percolate.spi.Directive}
 * the demand context carries into a strategy, derived from a discovered {@code @Map} {@link MappingDirective} and
 * the method's {@code @MapEnum} table. A pure adapter — it splits the dotted source path into segments, lifts the
 * {@code constant}/{@code defaultValue} members (already decided present/absent against the {@code Map.UNSET}
 * sentinel by discovery) into {@link java.util.Optional}, and carries the enum override table regardless of whether
 * a {@code @Map} directive is also bound at this path.
 */
@Tag('unit')
class BindingDirectiveSpec extends Specification {

    def 'splits a dotted source path into its segments'() {
        expect:
        binding('person.address.street', null, null, null, null).sourcePath() == ['person', 'address', 'street']
    }

    def 'a single-segment source yields a one-element path'() {
        expect:
        binding('name', null, null, null, null).sourcePath() == ['name']
    }

    def 'an absent or empty source yields an empty path'() {
        expect:
        binding(source, null, null, null, null).sourcePath() == []

        where:
        source << [null, '']
    }

    def 'constant and defaultValue are carried as present Optionals when set (empty string is present)'() {
        when:
        def directive = binding(null, '', 'fallback', null, null)

        then:
        directive.constant() == Optional.of('')
        directive.defaultValue() == Optional.of('fallback')
        directive.sourcePath() == []
    }

    def 'constant and defaultValue are empty Optionals when absent'() {
        when:
        def directive = binding('a', null, null, null, null)

        then:
        directive.constant() == Optional.empty()
        directive.defaultValue() == Optional.empty()
    }

    def 'format and zone are carried as present Optionals when set (empty string is present)'() {
        when:
        def directive = binding(null, null, null, '', 'Europe/Berlin')

        then:
        directive.format() == Optional.of('')
        directive.zone() == Optional.of('Europe/Berlin')
    }

    def 'format and zone are empty Optionals when absent'() {
        when:
        def directive = binding('a', null, null, null, null)

        then:
        directive.format() == Optional.empty()
        directive.zone() == Optional.empty()
    }

    def 'enumOverrides carries the MapEnum table alongside a present @Map directive'() {
        when:
        def directive = BindingDirective.from(Optional.of(directive('a', null, null, null, null)),
                [enumOverride('NEW', 'CREATED')])

        then:
        directive.enumOverrides()*.source == ['NEW']
        directive.enumOverrides()*.target == ['CREATED']
    }

    def 'enumOverrides carries the MapEnum table with no @Map directive at all'() {
        when:
        def directive = BindingDirective.from(Optional.empty(), [enumOverride('NEW', 'CREATED')])

        then:
        directive.enumOverrides()*.source == ['NEW']
        directive.sourcePath() == []
        directive.constant() == Optional.empty()
    }

    def 'enumOverrides is empty when no @MapEnum is declared'() {
        expect:
        binding('a', null, null, null, null).enumOverrides() == []
    }

    private static BindingDirective binding(final String source, final String constant, final String defaultValue,
                                              final String format, final String zone) {
        BindingDirective.from(Optional.of(directive(source, constant, defaultValue, format, zone)), [])
    }

    private static MappingDirective directive(final String source, final String constant, final String defaultValue,
                                               final String format, final String zone) {
        new MappingDirective('target', source, constant, defaultValue, format, zone, null, null, null, null, null, null, null)
    }

    private static EnumOverrideDirective enumOverride(final String source, final String target) {
        new EnumOverrideDirective(source, target, null, null, null)
    }
}
