package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

@Tag('unit')
class ConstructorCallSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()

    def 'emits no operation when the target type is not DECLARED'() {
        expect:
        new ConstructorCall().expand(Demands.forTarget(TypeUniverse.INT), ctx).toList().empty
    }

    def 'emits a multi-port assembly operation over the constructor parameters when the goal spec matches'() {
        given:
        def personRecord = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonRecord).asType()
        def declared = constructorParamNames(personRecord)

        when:
        def specs = new ConstructorCall().expand(Demands.assembling(personRecord, declared), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        ctx.types().isSameType(spec.outputType, personRecord)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
        // One port per constructor parameter, named after it, in declaration order. (Parameter *names* are
        // environment-dependent — this unit JVM may report arg0/arg1 — so the goal spec is derived from the
        // same constructor; the driver's name-based binding is covered by the processor specs.)
        spec.ports.size() == 2
        (spec.ports*.name as Set) == declared
        // Every assembly port is a structural sub-target: the engine mints a child demand at the child location.
        spec.ports.every { it.sourcing == Port.Sourcing.SUBTARGET }
        spec.ports[0].type.kind == TypeKind.INT
        ctx.types().isSameType(spec.ports[1].type, TypeUniverse.STRING)
    }

    def 'binds ports in constructor-parameter order for PersonByFieldOrder'() {
        given:
        def personByFieldOrder = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonByFieldOrder).asType()
        def declared = constructorParamNames(personByFieldOrder)

        when:
        def specs = new ConstructorCall().expand(Demands.assembling(personByFieldOrder, declared), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports.size() == 2
        specs[0].ports[0].type.kind == TypeKind.INT
        ctx.types().isSameType(specs[0].ports[1].type, TypeUniverse.STRING)
    }

    def 'rejects a constructor whose parameters do not match the declared-children goal spec'() {
        given:
        def personRecord = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonRecord).asType()

        expect:
        new ConstructorCall().expand(Demands.assembling(personRecord, ['nonexistent'] as Set), ctx).toList().empty
    }

    private static Set<String> constructorParamNames(final TypeMirror type) {
        def element = TypeUniverse.types().asElement(type)
        def ctor = element.enclosedElements.find { it.kind == ElementKind.CONSTRUCTOR } as ExecutableElement
        ctor.parameters.collect { it.simpleName.toString() } as Set
    }
}
