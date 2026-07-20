package io.github.joke.percolate.spi

import io.github.joke.percolate.javapoet.CodeBlock
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Exercises the {@link Conversion} archetype base through a tiny authored subclass: the author supplies only the input
 * types and per-input rendering ({@link Conversion#conversions}); the base wires each into a one-port, candidate-free,
 * target-driven {@link OperationSpec}. Unit-tested mock-only: the base itself never asks the {@link ResolveCtx} seam
 * anything, so every {@link TypeMirror} is an opaque never-interrogated token, compared only by identity.
 */
@Tag('unit')
class ConversionSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror stringType = Mock()
    TypeMirror integerType = Mock()

    def 'the base wires each authored conversion into a one-port NON_NULL unary OperationSpec'() {
        def specs = new TestConversion(stringType, integerType).expand(demand(integerType), ctx).toList()

        expect:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'String→Integer'
        spec.weight == Weights.STEP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        spec.ports[0].sourcing == Port.Sourcing.REUSE_OR_MINT
        spec.ports[0].template == null
        spec.ports[0].type.is(stringType)
        spec.ports[0].nullness == Nullability.NON_NULL
        spec.outputType.is(integerType)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'a target the author offers no conversion for yields no spec'() {
        expect:
        new TestConversion(stringType, integerType).expand(demand(stringType), ctx).toList().empty
    }

    def 'the base wires every authored conversion when several inputs produce the same target'() {
        TypeMirror booleanType = Mock()

        when:
        def specs = new TestTwoWayConversion(stringType, booleanType, integerType).expand(demand(integerType), ctx).toList()

        then:
        specs.size() == 2
        specs*.label as Set == ['String→Integer', 'Boolean→Integer'] as Set
        specs.every { it.outputType.is(integerType) }
        specs.every { it.outputNullness == Nullability.NON_NULL }
        specs.find { it.label == 'String→Integer' }.ports[0].type.is(stringType)
        specs.find { it.label == 'Boolean→Integer' }.ports[0].type.is(booleanType)
    }

    private static ProduceDemand demand(final TypeMirror target) {
        [
                targetType      : { target },
                targetNullness  : { Nullability.NON_NULL },
                directive       : { Optional.empty() },
                declaredChildren: { [] as Set },
                bindingName     : { '' },
                nullnessOf      : { TypeMirror t, Element s -> Nullability.NON_NULL },
        ] as ProduceDemand
    }

    /** Offers a single String→target conversion, but only when the target is the authored Integer token. */
    static class TestConversion extends Conversion {
        private final TypeMirror stringType
        private final TypeMirror integerType

        TestConversion(final TypeMirror stringType, final TypeMirror integerType) {
            this.stringType = stringType
            this.integerType = integerType
        }

        @Override
        protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx c) {
            if (!target.is(integerType)) {
                return Stream.empty()
            }
            def codegen = { IncomingValues inputs -> CodeBlock.of('$T.valueOf($L)', Integer, inputs.single()) } as OperationCodegen
            Stream.of(new Step(stringType, 'String→Integer', Weights.STEP, codegen))
        }
    }

    /** Offers two conversions (String and Boolean) producing the same authored target. */
    static class TestTwoWayConversion extends Conversion {
        private final TypeMirror stringType
        private final TypeMirror booleanType
        private final TypeMirror integerType

        TestTwoWayConversion(final TypeMirror stringType, final TypeMirror booleanType, final TypeMirror integerType) {
            this.stringType = stringType
            this.booleanType = booleanType
            this.integerType = integerType
        }

        @Override
        protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx c) {
            if (!target.is(integerType)) {
                return Stream.empty()
            }
            def stringCodegen = { IncomingValues inputs -> CodeBlock.of('$T.valueOf($L)', Integer, inputs.single()) } as OperationCodegen
            def booleanCodegen = { IncomingValues inputs -> CodeBlock.of('$L ? 1 : 0', inputs.single()) } as OperationCodegen
            Stream.of(
                    new Step(stringType, 'String→Integer', Weights.STEP, stringCodegen),
                    new Step(booleanType, 'Boolean→Integer', Weights.STEP, booleanCodegen))
        }
    }
}
