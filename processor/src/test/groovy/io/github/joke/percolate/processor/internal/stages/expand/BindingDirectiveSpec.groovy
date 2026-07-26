package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.model.EnumOverrideDirective
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.test.MappingDirectives
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link BindingDirective} seam, unit-tested directly: the per-binding {@link io.github.joke.percolate.spi.Directive}
 * the demand context carries into a strategy (design D3 of change {@code decouple-engine-from-strategy-semantics}),
 * derived from a discovered {@code @Map} {@link MappingDirective} and the method's {@code @MapEnum} table. A pure
 * adapter — it splits the dotted source path into segments, forwards {@code constant}/{@code defaultValue}/{@code
 * format}/{@code zone} (already decided present/absent by discovery) unchanged as the open bag, and turns each
 * {@code @MapEnum} entry into one repeated, structured {@code "enum"} input regardless of whether a {@code @Map}
 * directive is also bound at this path.
 */
@Tag('unit')
class BindingDirectiveSpec extends Specification {

    def 'splits a dotted source path into its segments'() {
        expect:
        binding('person.address.street', [:]).sourcePath() == ['person', 'address', 'street']
    }

    def 'a single-segment source yields a one-element path'() {
        expect:
        binding('name', [:]).sourcePath() == ['name']
    }

    def 'an absent or empty source yields an empty path'() {
        expect:
        binding(source, [:]).sourcePath() == []

        where:
        source << [null, '']
    }

    def 'constant and defaultValue are carried as present values when set (empty string is present)'() {
        when:
        def directive = binding(null, [constant: '', defaultValue: 'fallback'])

        then:
        directive.value('constant') == Optional.of('')
        directive.value('defaultValue') == Optional.of('fallback')
        directive.sourcePath() == []
    }

    def 'constant and defaultValue are empty Optionals when absent'() {
        when:
        def directive = binding('a', [:])

        then:
        directive.value('constant') == Optional.empty()
        directive.value('defaultValue') == Optional.empty()
    }

    def 'format and zone are carried as present values when set (empty string is present)'() {
        when:
        def directive = binding(null, [format: '', zone: 'Europe/Berlin'])

        then:
        directive.value('format') == Optional.of('')
        directive.value('zone') == Optional.of('Europe/Berlin')
    }

    def 'format and zone are empty Optionals when absent'() {
        when:
        def directive = binding('a', [:])

        then:
        directive.value('format') == Optional.empty()
        directive.value('zone') == Optional.empty()
    }

    def 'each @MapEnum entry becomes one repeated, structured "enum" input alongside a present @Map directive'() {
        when:
        def directive = BindingDirective.from(Optional.of(directive('a', [:])), [enumOverride('NEW', 'CREATED')])

        then:
        directive.inputs('enum').size() == 1
        directive.inputs('enum')[0].member('source') == Optional.of('NEW')
        directive.inputs('enum')[0].member('target') == Optional.of('CREATED')
    }

    def '"enum" inputs are carried with no @Map directive at all'() {
        when:
        def directive = BindingDirective.from(Optional.empty(), [enumOverride('NEW', 'CREATED')])

        then:
        directive.inputs('enum').size() == 1
        directive.inputs('enum')[0].member('source') == Optional.of('NEW')
        directive.sourcePath() == []
        directive.value('constant') == Optional.empty()
    }

    def 'no "enum" input is carried when no @MapEnum is declared'() {
        expect:
        binding('a', [:]).inputs('enum') == []
    }

    private static BindingDirective binding(final String source, final Map<String, String> members) {
        BindingDirective.from(Optional.of(directive(source, members)), [])
    }

    private static MappingDirective directive(final String source, final Map<String, String> members) {
        def all = source == null ? members : members + [source: source]
        MappingDirectives.of('target', all)
    }

    private static EnumOverrideDirective enumOverride(final String source, final String target) {
        new EnumOverrideDirective(source, target, Subjects.none(), Subjects.none())
    }
}
