package io.github.joke.percolate.processor.model

import io.github.joke.percolate.processor.test.MappingDirectives
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class GoalSpecSpec extends Specification {

    def 'nested target paths group declared children by level'() {
        given:
        final var spec = GoalSpec.from([directive('address.street', 'p.street'), directive('address.zip', 'p.zip')])

        expect:
        spec.declaredChildren('') == ['address'] as Set
        spec.declaredChildren('address') == ['street', 'zip'] as Set
    }

    def 'a leaf binding is reachable by its exact target path; a structural level has none'() {
        given:
        final var spec = GoalSpec.from([directive('address.street', 'p.street')])

        expect:
        spec.bindingFor('address.street').present
        spec.bindingFor('address.street').get().source == 'p.street'
        spec.bindingFor('address').empty
    }

    def 'a constant directive participates as a declared binding'() {
        given:
        final var spec = GoalSpec.from([constant('number', '42')])

        expect:
        spec.declaredChildren('') == ['number'] as Set
        spec.bindingFor('number').present
        spec.bindingFor('number').get().constant == '42'
    }

    def 'an unknown level declares no children'() {
        expect:
        GoalSpec.from([]).declaredChildren('anything').empty
    }

    def 'the one-arg factory carries no enum overrides'() {
        expect:
        GoalSpec.from([directive('address.street', 'p.street')]).enumOverrides == []
    }

    def 'the two-arg factory carries the given enum overrides, in order, alongside the @Map bindings'() {
        def overrides = [new EnumOverrideDirective('NEW', 'CREATED', Subjects.none(), Subjects.none()),
                new EnumOverrideDirective('DONE', 'FULFILLED', Subjects.none(), Subjects.none())]

        when:
        def spec = GoalSpec.from([directive('address.street', 'p.street')], overrides)

        then:
        spec.enumOverrides == overrides
        spec.bindingFor('address.street').present
    }

    def 'splitPath returns no segments for a null or empty path, else splits on dots'() {
        expect:
        GoalSpec.splitPath(null) == []
        GoalSpec.splitPath('') == []
        GoalSpec.splitPath('address.street') == ['address', 'street']
    }

    private static MappingDirective directive(final String target, final String source) {
        MappingDirectives.of(target, [source: source])
    }

    private static MappingDirective constant(final String target, final String value) {
        MappingDirectives.of(target, [constant: value])
    }
}
