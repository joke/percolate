package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Candidate
import io.github.joke.percolate.spi.Directive
import io.github.joke.percolate.spi.Frontier
import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class ConstructorCallSpec extends Specification {

    def 'emits no step when the target type is not DECLARED'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new ConstructorCall().expand(frontierFor(TypeUniverse.INT), ctx).toList()

        then:
        steps.empty
    }

    def 'emits a multi-slot BOUNDARY step over the constructor parameters for PersonRecord'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personRecord = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord').asType()

        when:
        def steps = new ConstructorCall().expand(frontierFor(personRecord), ctx).toList()

        then:
        steps.size() == 1
        def step = steps[0]
        step.intent == Intent.BOUNDARY
        step.scope.empty
        ctx.types().isSameType(step.output, personRecord)
        // One slot per constructor parameter, in declaration order. (Parameter *names* are environment-dependent —
        // this unit JVM reports arg0/arg1; the driver's name-based slot binding is covered by the processor specs.)
        step.inputs.size() == 2
        step.inputs[0].type.kind == javax.lang.model.type.TypeKind.INT
        step.inputs[0].weight == Weights.STEP
        ctx.types().isSameType(step.inputs[1].type, TypeUniverse.STRING)
        step.inputs[1].weight == Weights.STEP
        // Every slot carries its originating parameter element for the consumer nullability contract.
        step.inputs.every { it.producedFrom != null }
    }

    def 'binds slots in constructor-parameter order for PersonByFieldOrder'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personByFieldOrder = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonByFieldOrder').asType()

        when:
        def steps = new ConstructorCall().expand(frontierFor(personByFieldOrder), ctx).toList()

        then:
        steps.size() == 1
        def step = steps[0]
        step.inputs.size() == 2
        step.inputs[0].type.kind == javax.lang.model.type.TypeKind.INT
        ctx.types().isSameType(step.inputs[1].type, TypeUniverse.STRING)
    }

    private static Frontier frontierFor(final TypeMirror target) {
        new Frontier() {
            TypeMirror targetType() { target }
            Optional<Directive> directive() { Optional.empty() }
            List<Candidate> candidates() { [] }
        }
    }
}
