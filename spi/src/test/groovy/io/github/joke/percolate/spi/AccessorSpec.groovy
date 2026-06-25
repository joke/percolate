package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * Exercises the {@link Accessor} archetype base through a tiny authored subclass. The base answers the descend
 * question: it reads the concrete parent type and the single segment from a {@link DescendDemand}, declines a
 * non-declared parent, and wires the one-port accessor {@link OperationSpec} (typing the produced nullness through the
 * demand oracle). The author supplies only {@link Accessor#accessor}: the member match and its rendering.
 */
@Tag('unit')
class AccessorSpec extends Specification {

    @Shared ResolveCtx ctx = ctx()

    def 'the base wires a one-port accessor spec from a single segment on a declared parent'() {
        when:
        def specs = new TestAccessor().descend(descend(TypeUniverse.STRING, 'known'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'known()'
        spec.weight == 7
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        spec.ports[0].sourcing == Port.Sourcing.REUSE_OR_MINT
        ctx.types().isSameType(spec.ports[0].type, TypeUniverse.STRING)
        spec.ports[0].nullness == Nullability.NON_NULL
        ctx.types().isSameType(spec.outputType, TypeUniverse.INTEGER)
        spec.outputNullness == Nullability.NULLABLE
    }

    def 'a non-declared parent yields no accessor'() {
        expect:
        new TestAccessor().descend(descend(TypeUniverse.INT, 'known'), ctx).toList().empty
    }

    def 'an unresolved segment yields no accessor'() {
        expect:
        new TestAccessor().descend(descend(TypeUniverse.STRING, 'missing'), ctx).toList().empty
    }

    private static DescendDemand descend(final TypeMirror parent, final String segment) {
        [
                parentType    : { parent },
                parentNullness: { Nullability.NON_NULL },
                segment       : { segment },
                nullnessOf    : { TypeMirror t, Element s -> Nullability.NULLABLE },
        ] as DescendDemand
    }

    private static ResolveCtx ctx() {
        [
                elements       : { TypeUniverse.elements() },
                types          : { TypeUniverse.types() },
                callableMethods: { null },
        ] as ResolveCtx
    }

    /** Resolves only the segment {@code known}, producing an Integer-typed access rendered as {@code parent.known()}. */
    static class TestAccessor extends Accessor {
        @Override
        protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx c) {
            if (segment != 'known') {
                return Optional.empty()
            }
            def codegen = { IncomingValues inputs -> CodeBlock.of('$L.known()', inputs.single()) } as OperationCodegen
            Optional.of(new Step(TypeUniverse.INTEGER, parent, 'known()', 7, codegen))
        }
    }
}
