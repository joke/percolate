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
class DirectAssignSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared def ctx = new ResolveCtxBuilder(javac).build()
    @Shared def types = javac.types()

    def 'emits a zero-cost identity operation that produces the demanded target'() {
        when:
        def specs = new DirectAssign().expand(Demands.forTarget(javac.STRING), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        types.isSameType(spec.ports[0].type, javac.STRING)
        types.isSameType(spec.outputType, javac.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'its single port is reuse-only: the driver binds an in-scope same-type source or the op does not apply'() {
        when:
        def specs = new DirectAssign().expand(Demands.forTarget(javac.STRING), ctx).toList()

        then: 'never minted — a same-type value already feeds the target directly (no self-copy manufacturing)'
        specs[0].ports[0].sourcing == Port.Sourcing.REUSE
    }

    def 'is nullness-transparent: port and output carry the demanded nullness'() {
        when:
        def demand = Demands.forTarget(javac.STRING, Nullability.NULLABLE)
        def specs = new DirectAssign().expand(demand, ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports[0].nullness == Nullability.NULLABLE
        specs[0].outputNullness == Nullability.NULLABLE
    }
}
