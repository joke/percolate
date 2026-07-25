package io.github.joke.percolate.processor.model

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
        def overrides = [new EnumOverrideDirective('NEW', 'CREATED', null, null, null),
                new EnumOverrideDirective('DONE', 'FULFILLED', null, null, null)]

        when:
        def spec = GoalSpec.from([directive('address.street', 'p.street')], overrides)

        then:
        spec.enumOverrides == overrides
        spec.bindingFor('address.street').present
    }

    private static MappingDirective directive(final String target, final String source) {
        new MappingDirective(target, source, null, null, null, null, null, null, null, null, null, null, null)
    }

    private static MappingDirective constant(final String target, final String value) {
        new MappingDirective(target, null, value, null, null, null, null, null, null, null, null, null, null)
    }
}
