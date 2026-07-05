package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
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
}
