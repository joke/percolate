package io.github.joke.percolate.spi

import io.github.joke.percolate.javapoet.CodeBlock
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
 * Unit-tested mock-only over the {@link ResolveCtx} type-query seam (change {@code type-query-seam}): the sole seam
 * question the base asks — {@code asTypeElement} — is stubbed on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque never-interrogated token. No javac.
 */
@Tag('unit')
class AccessorSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror stringType = Mock()
    TypeMirror intType = Mock()
    TypeMirror integerType = Mock()
    TypeElement parentElement = Mock()

    def 'the base wires a one-port accessor spec from a single segment on a declared parent'() {
        ctx.asTypeElement(stringType) >> Optional.of(parentElement)

        when:
        def specs = new TestAccessor(integerType).descend(descend(stringType, 'known'), ctx).toList()

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
        spec.ports[0].type.is(stringType)
        spec.ports[0].nullness == Nullability.NON_NULL
        spec.outputType.is(integerType)
        spec.outputNullness == Nullability.NULLABLE
    }

    def 'a non-declared parent yields no accessor'() {
        ctx.asTypeElement(intType) >> Optional.empty()

        expect:
        new TestAccessor(integerType).descend(descend(intType, 'known'), ctx).toList().empty
    }

    def 'an unresolved segment yields no accessor'() {
        ctx.asTypeElement(stringType) >> Optional.of(parentElement)

        expect:
        new TestAccessor(integerType).descend(descend(stringType, 'missing'), ctx).toList().empty
    }

    private static DescendDemand descend(final TypeMirror parent, final String segment) {
        [
                parentType    : { parent },
                parentNullness: { Nullability.NON_NULL },
                segment       : { segment },
                nullnessOf    : { TypeMirror t, Element s -> Nullability.NULLABLE },
        ] as DescendDemand
    }

    /** Resolves only the segment {@code known}, producing an {@code outputType}-typed access rendered as {@code parent.known()}. */
    static class TestAccessor extends Accessor {
        private final TypeMirror outputType

        TestAccessor(final TypeMirror outputType) {
            this.outputType = outputType
        }

        @Override
        protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx c) {
            if (segment != 'known') {
                return Optional.empty()
            }
            def codegen = { IncomingValues inputs -> CodeBlock.of('$L.known()', inputs.single()) } as OperationCodegen
            Optional.of(new Step(outputType, parent, 'known()', 7, codegen))
        }
    }
}
