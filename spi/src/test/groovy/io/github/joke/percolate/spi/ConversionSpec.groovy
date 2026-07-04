package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Exercises the {@link Conversion} archetype base through a tiny authored subclass: the author supplies only the input
 * types and per-input rendering ({@link Conversion#conversions}); the base wires each into a one-port, candidate-free,
 * target-driven {@link OperationSpec}.
 */
@Tag('unit')
class ConversionSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared ResolveCtx ctx = ctx()

    def 'the base wires each authored conversion into a one-port NON_NULL unary OperationSpec'() {
        when:
        def specs = new TestConversion(javac).expand(demand(javac.INTEGER), ctx).toList()

        then:
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
        ctx.types().isSameType(spec.ports[0].type, javac.STRING)
        spec.ports[0].nullness == Nullability.NON_NULL
        ctx.types().isSameType(spec.outputType, javac.INTEGER)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'a target the author offers no conversion for yields no spec'() {
        expect:
        new TestConversion(javac).expand(demand(javac.STRING), ctx).toList().empty
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

    private ResolveCtx ctx() {
        [
                elements       : { javac.elements() },
                types          : { javac.types() },
                callableMethods: { null },
        ] as ResolveCtx
    }

    /** Offers a single String→target conversion, but only when the target is Integer. */
    static class TestConversion extends Conversion {
        private final PrivateTypeUniverse javac

        TestConversion(final PrivateTypeUniverse javac) {
            this.javac = javac
        }

        @Override
        protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx c) {
            if (!c.types().isSameType(target, javac.INTEGER)) {
                return Stream.empty()
            }
            def codegen = { IncomingValues inputs -> CodeBlock.of('$T.valueOf($L)', Integer, inputs.single()) } as OperationCodegen
            Stream.of(new Step(javac.STRING, 'String→Integer', Weights.STEP, codegen))
        }
    }
}
