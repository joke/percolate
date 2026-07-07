package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Receiver
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link MethodCallBridge} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): candidate filtering and spec assembly are driven by a mocked
 * {@code CallableMethods}/{@code ResolveCtx} over opaque tokens. No javac. The subtype-distance walk it delegates to
 * is covered on its own in {@link SubtypeDistanceSpec}; here the seam supplies just enough (same-type, distance 0)
 * for the real {@link SubtypeDistance} collaborator to resolve without further stubbing.
 */
@Tag('unit')
class MethodCallBridgeSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror target = Mock()

    def 'returns empty when callableMethods is null'() {
        ctx.callableMethods() >> null

        expect:
        new MethodCallBridge().expand(Demands.forTarget(target), ctx).toList().empty
    }

    def 'returns empty when callableMethods produces an empty stream'() {
        CallableMethods callableMethods = Mock()
        ctx.callableMethods() >> callableMethods
        callableMethods.producing(target) >> Stream.empty()

        expect:
        new MethodCallBridge().expand(Demands.forTarget(target), ctx).toList().empty
    }

    def 'emits a one-port call operation when CallableMethods provides a matching candidate'() {
        CallableMethods callableMethods = Mock()
        ExecutableElement method = Mock()
        VariableElement param = Mock()
        TypeMirror paramType = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        ctx.callableMethods() >> callableMethods
        callableMethods.producing(target) >> Stream.of(candidate)
        method.parameters >> [param]
        method.returnType >> target
        method.simpleName >> nameOf('concat')
        param.simpleName >> nameOf('arg')
        param.asType() >> paramType
        ctx.isAssignable(target, target) >> true
        ctx.isSameType(target, target) >> true
        receiver.asExpression() >> com.palantir.javapoet.CodeBlock.of('obj')

        when:
        def specs = new MethodCallBridge().expand(Demands.forTarget(target), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.weight >= Weights.METHOD
        spec.outputType.is(target)
        spec.outputNullness == Nullability.NON_NULL
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }
}
