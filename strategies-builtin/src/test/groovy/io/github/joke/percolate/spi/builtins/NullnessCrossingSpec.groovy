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

    def 'emits a partial requireNonNull for a nullable reference scalar feeding a non-null demand'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.STRING, TypeUniverse.STRING, Nullability.NULLABLE, 'name'), ctx)
                .toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        types.isSameType(spec.ports[0].type, TypeUniverse.STRING)
        spec.ports[0].nullness == Nullability.NULLABLE
        types.isSameType(spec.outputType, TypeUniverse.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'a non-null candidate needs no crossing (pass-through)'() {
        expect:
        new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.STRING, TypeUniverse.STRING, Nullability.NON_NULL, 'name'), ctx)
                .toList()
                .empty
    }

    def 'a nullable demand needs no crossing'() {
        expect:
        new NullnessCrossing()
                .expand(Demands.forTarget(TypeUniverse.STRING, [TypeUniverse.STRING], Nullability.NULLABLE), ctx)
                .toList()
                .empty
    }

    def 'a default on a nullable scalar coalesces (total) alongside the partial requireNonNull'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.STRING, TypeUniverse.STRING, Nullability.NULLABLE, 'name', 'unknown'),
                        ctx)
                .toList()

        then: 'a total coalesce over a NULLABLE scalar port'
        def coalesce = specs.find { !it.partial }
        coalesce != null
        coalesce.weight == Weights.NOOP
        coalesce.childScope.empty
        types.isSameType(coalesce.ports[0].type, TypeUniverse.STRING)
        coalesce.ports[0].nullness == Nullability.NULLABLE
        types.isSameType(coalesce.outputType, TypeUniverse.STRING)
        coalesce.outputNullness == Nullability.NON_NULL

        and: 'the partial requireNonNull is also offered (totality picks coalesce in extraction)'
        specs.any { it.partial }
    }

    def 'a default on an Optional source coalesces over a present Optional port and no requireNonNull'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.STRING, optionalOfString, Nullability.NON_NULL, 'name', 'unknown'),
                        ctx)
                .toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        !spec.partial
        types.isSameType(spec.ports[0].type, optionalOfString)
        spec.ports[0].nullness == Nullability.NON_NULL
        types.isSameType(spec.outputType, TypeUniverse.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'coerces the default literal to a wrapper target type'() {
        when:
        def specs = new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.INTEGER, TypeUniverse.INTEGER, Nullability.NULLABLE, 'n', '0'), ctx)
                .toList()

        then:
        def coalesce = specs.find { !it.partial }
        coalesce != null
        types.isSameType(coalesce.outputType, TypeUniverse.INTEGER)
        coalesce.ports[0].nullness == Nullability.NULLABLE
    }

    def 'emits nothing for a primitive source (a primitive can never be absent)'() {
        expect:
        new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.INT, TypeUniverse.INT, Nullability.NON_NULL, 'n', '0'), ctx)
                .toList()
                .empty
    }

    def 'emits nothing for an uncoercible default'() {
        expect:
        new NullnessCrossing()
                .expand(Demands.crossing(TypeUniverse.INT, TypeUniverse.INT, Nullability.NON_NULL, 'n', 'abc'), ctx)
                .toList()
                .empty
    }
}
