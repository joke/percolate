package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class NullnessCrossingSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()
    def optionalOfString = types.getDeclaredType(TypeUniverse.element('java.util.Optional'), TypeUniverse.STRING)

    def 'emits a partial requireNonNull for a non-null reference-scalar demand'() {
        when:
        def specs = new NullnessCrossing().expand(Demands.crossing(TypeUniverse.STRING, 'name'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        types.isSameType(spec.ports[0].type, TypeUniverse.STRING)
        spec.ports[0].nullness == Nullability.NULLABLE
        spec.ports[0].reuseOnly
        types.isSameType(spec.outputType, TypeUniverse.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'a nullable demand needs no crossing'() {
        expect:
        new NullnessCrossing()
                .expand(Demands.forTarget(TypeUniverse.STRING, Nullability.NULLABLE), ctx)
                .toList()
                .empty
    }

    def 'a default over-emits a total scalar coalesce and the Optional coalesce alongside the partial requireNonNull'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.STRING, 'name', 'unknown'), ctx)
                .toList()

        then: 'a total coalesce over a NULLABLE scalar port'
        def scalar = specs.find { !it.partial && types.isSameType(it.ports[0].type, TypeUniverse.STRING) }
        scalar != null
        scalar.weight == Weights.NOOP
        scalar.childScope.empty
        scalar.ports[0].nullness == Nullability.NULLABLE
        types.isSameType(scalar.outputType, TypeUniverse.STRING)
        scalar.outputNullness == Nullability.NON_NULL

        and: 'a total coalesce over a present Optional<String> port'
        def optional = specs.find { !it.partial && types.isSameType(it.ports[0].type, optionalOfString) }
        optional != null
        optional.ports[0].nullness == Nullability.NON_NULL
        types.isSameType(optional.outputType, TypeUniverse.STRING)

        and: 'the partial requireNonNull is also offered (totality picks coalesce in extraction)'
        specs.any { it.partial }

        and: 'every crossing port is reuse-only — the driver binds an in-scope source or the op does not apply'
        specs.every { it.ports[0].reuseOnly }
    }

    def 'coerces the default literal to a wrapper target type'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.INTEGER, 'n', '0'), ctx)
                .toList()

        then:
        def scalar = specs.find { !it.partial && types.isSameType(it.ports[0].type, TypeUniverse.INTEGER) }
        scalar != null
        types.isSameType(scalar.outputType, TypeUniverse.INTEGER)
        scalar.ports[0].nullness == Nullability.NULLABLE
    }

    def 'emits nothing for a primitive target (a primitive can never be absent)'() {
        expect:
        new NullnessCrossing().expand(Demands.crossing(TypeUniverse.INT, 'n', '0'), ctx).toList().empty
    }

    def 'an uncoercible default yields only the guard, no coalesce'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.INTEGER, 'n', 'abc'), ctx)
                .toList()

        then: 'the requireNonNull guard remains (NON_NULL declared target) but no total coalesce is offered'
        specs.every { it.partial }
    }
}
