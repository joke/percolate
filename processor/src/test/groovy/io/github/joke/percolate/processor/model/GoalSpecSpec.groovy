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

    private static MappingDirective directive(final String target, final String source) {
        new MappingDirective(target, source, null, null, null, null, null, null, null, null, null, null, null)
    }

    private static MappingDirective constant(final String target, final String value) {
        new MappingDirective(target, null, value, null, null, null, null, null, null, null, null, null, null)
    }
}
