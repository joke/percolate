package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link DirectAssign} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): the strategy asks the seam no questions at all, so the mocked
 * {@code ResolveCtx} is never stubbed; the target {@link TypeMirror} is an opaque, never-interrogated token.
 */
@Tag('unit')
class DirectAssignSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror target = Mock()

    def 'emits a zero-cost identity operation that produces the demanded target'() {
        when:
        def specs = new DirectAssign().expand(Demands.forTarget(target), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        spec.ports[0].type.is(target)
        spec.outputType.is(target)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'its single port is reuse-only: the driver binds an in-scope same-type source or the op does not apply'() {
        when:
        def specs = new DirectAssign().expand(Demands.forTarget(target), ctx).toList()

        then: 'never minted — a same-type value already feeds the target directly (no self-copy manufacturing)'
        specs[0].ports[0].sourcing == Port.Sourcing.REUSE
    }

    def 'is nullness-transparent: port and output carry the demanded nullness'() {
        when:
        def demand = Demands.forTarget(target, Nullability.NULLABLE)
        def specs = new DirectAssign().expand(demand, ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports[0].nullness == Nullability.NULLABLE
        specs[0].outputNullness == Nullability.NULLABLE
    }

    def 'is labeled assign and renders the identity of its single input'() {
        when:
        def specs = new DirectAssign().expand(Demands.forTarget(target), ctx).toList()

        then:
        specs[0].label == 'assign'
        specs[0].codegen.render(singleInput(io.github.joke.percolate.lib.javapoet.CodeBlock.of('$N', 'x'))).toString() == 'x'
    }

    private static io.github.joke.percolate.spi.IncomingValues singleInput(final io.github.joke.percolate.lib.javapoet.CodeBlock value) {
        [single: { -> value }] as io.github.joke.percolate.spi.IncomingValues
    }
}
