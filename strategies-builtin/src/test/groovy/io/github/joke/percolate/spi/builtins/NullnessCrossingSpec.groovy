package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class NullnessCrossingSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared def ctx = new ResolveCtxBuilder(javac).build()
    @Shared def types = javac.types()
    @Shared def optionalOfString = types.getDeclaredType(javac.element('java.util.Optional'), javac.STRING)

    def 'emits a partial requireNonNull for a non-null reference-scalar demand'() {
        when:
        def specs = new NullnessCrossing().expand(Demands.crossing(javac.STRING, 'name'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        types.isSameType(spec.ports[0].type, javac.STRING)
        spec.ports[0].nullness == Nullability.NULLABLE
        spec.ports[0].sourcing == Port.Sourcing.REUSE
        types.isSameType(spec.outputType, javac.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'a nullable demand needs no crossing'() {
        expect:
        new NullnessCrossing()
                .expand(Demands.forTarget(javac.STRING, Nullability.NULLABLE), ctx)
                .toList()
                .empty
    }

    def 'a default over-emits a total scalar coalesce and the Optional coalesce alongside the partial requireNonNull'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(javac.STRING, 'name', 'unknown'), ctx)
                .toList()

        then: 'a total coalesce over a NULLABLE scalar port'
        def scalar = specs.find { !it.partial && types.isSameType(it.ports[0].type, javac.STRING) }
        scalar != null
        scalar.weight == Weights.NOOP
        scalar.childScope.empty
        scalar.ports[0].nullness == Nullability.NULLABLE
        types.isSameType(scalar.outputType, javac.STRING)
        scalar.outputNullness == Nullability.NON_NULL

        and: 'a total coalesce over a present Optional<String> port'
        def optional = specs.find { !it.partial && types.isSameType(it.ports[0].type, optionalOfString) }
        optional != null
        optional.ports[0].nullness == Nullability.NON_NULL
        types.isSameType(optional.outputType, javac.STRING)

        and: 'the partial requireNonNull is also offered (totality picks coalesce in extraction)'
        specs.any { it.partial }

        and: 'every crossing port is REUSE — the driver binds an in-scope source or the op does not apply'
        specs.every { it.ports[0].sourcing == Port.Sourcing.REUSE }
    }

    def 'coerces the default literal to a wrapper target type'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(javac.INTEGER, 'n', '0'), ctx)
                .toList()

        then:
        def scalar = specs.find { !it.partial && types.isSameType(it.ports[0].type, javac.INTEGER) }
        scalar != null
        types.isSameType(scalar.outputType, javac.INTEGER)
        scalar.ports[0].nullness == Nullability.NULLABLE
    }

    def 'emits nothing for a primitive target (a primitive can never be absent)'() {
        expect:
        new NullnessCrossing().expand(Demands.crossing(javac.INT, 'n', '0'), ctx).toList().empty
    }

    def 'an uncoercible default yields only the guard, no coalesce'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(javac.INTEGER, 'n', 'abc'), ctx)
                .toList()

        then: 'the requireNonNull guard remains (NON_NULL declared target) but no total coalesce is offered'
        specs.every { it.partial }
    }
}
