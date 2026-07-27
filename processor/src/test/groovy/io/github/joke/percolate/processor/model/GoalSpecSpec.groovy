package io.github.joke.percolate.processor.model

import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class GoalSpecSpec extends Specification {

    def 'nested target paths group declared children by level'() {
        given:
        final var spec = GoalSpec.from([bind('address.street', 'p.street'), bind('address.zip', 'p.zip')], [:], [:], [])

        expect:
        spec.declaredChildren('') == ['address'] as Set
        spec.declaredChildren('address') == ['street', 'zip'] as Set
    }

    def 'a leaf binding is reachable by its exact target path; a structural level has none'() {
        given:
        final var spec = GoalSpec.from([bind('address.street', 'p.street')], [:], [:], [])

        expect:
        spec.bindingFor('address.street').present
        spec.bindingFor('address.street').get().sourcePath() == ['p', 'street']
        spec.bindingFor('address').empty
    }

    def 'a constant-only binding (no source path) still participates as a declared binding'() {
        given:
        final var inputs = [DirectiveInput.scalar('constant', '42', Subjects.none())]
        final var spec = GoalSpec.from([new Bind(['number'], [], Subjects.none())], ['number': inputs], [:], [])

        expect:
        spec.declaredChildren('') == ['number'] as Set
        spec.bindingFor('number').present
        spec.bindingFor('number').get().value('constant').get() == '42'
    }

    def 'an unknown level declares no children'() {
        expect:
        GoalSpec.empty().declaredChildren('anything').empty
    }

    def 'a path with only attached inputs (no bind) is still assembled, e.g. a method-level @MapEnum table'() {
        given:
        final var inputs = [DirectiveInput.structured('enum', [source: 'NEW', target: 'CREATED'], Subjects.none())]
        final var spec = GoalSpec.from([], ['': inputs], [:], [])

        expect:
        spec.bindingFor('').present
        spec.bindingFor('').get().inputs() == inputs
        spec.declaredChildren('').empty
    }

    def 'constraints are reachable by their exact target path'() {
        given:
        final var constraint = { candidate, boundPorts -> Optional.empty() }
        final var spec = GoalSpec.from([], [:], ['address': [constraint]], [])

        expect:
        spec.constraintsFor('address') == [constraint]
        spec.constraintsFor('unbound').empty
    }

    def 'scope-input overrides travel with the goal spec, in declaration order'() {
        given:
        final var overrides = [Mock(ScopeInputOverride), Mock(ScopeInputOverride)]
        final var spec = GoalSpec.from([], [:], [:], overrides)

        expect:
        spec.scopeInputOverrides == overrides
    }

    private static Bind bind(final String target, final String source) {
        new Bind(target.split('\\.').toList(), source.split('\\.').toList(), Subjects.none())
    }
}
