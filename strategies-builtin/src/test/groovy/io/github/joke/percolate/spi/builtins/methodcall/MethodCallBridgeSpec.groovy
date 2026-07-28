package io.github.joke.percolate.spi.builtins.methodcall

import io.github.joke.percolate.Ambient
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
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
 * {@code cutover-strategies-to-mock-seam}, extended by {@code decouple-engine-from-strategy-semantics}):
 * candidate filtering and spec assembly are driven by a mocked {@code CallableMethods}/{@code ResolveCtx} over
 * opaque tokens. No javac. The subtype-distance walk it delegates to is covered on its own in
 * {@link SubtypeDistanceSpec}; here the seam supplies just enough (same-type, distance 0) for the real
 * {@link SubtypeDistance} collaborator to resolve without further stubbing. The strategy reads {@code @Ambient}
 * itself — an unstubbed {@code param.getAnnotation(Ambient)} on a plain (non-{@code @Ambient}) mocked parameter
 * returns {@code null}, so a plain parameter needs no explicit stubbing.
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
        param.getAnnotation(Ambient) >> null
        ctx.isAssignable(target, target) >> true
        ctx.isSameType(target, target) >> true
        receiver.asExpression() >> CodeBlock.of('obj')

        when:
        def specs = new MethodCallBridge().expand(Demands.forTarget(target), ctx)*.spec

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.ports[0].selector == Port.Selector.BY_TYPE
        spec.ports[0].onMiss == Port.OnMiss.MINT
        spec.weight >= Weights.METHOD
        spec.outputType.is(target)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'a demand with declared children is never bridged by a method call (assembly wins)'() {
        expect:
        new MethodCallBridge().expand(Demands.assembling(target, ['x'] as Set), ctx).toList().empty
    }

    def 'rejects a candidate whose method takes more than one non-ambient parameter'() {
        CallableMethods callableMethods = Mock()
        ExecutableElement method = Mock()
        VariableElement first = Mock()
        VariableElement second = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        ctx.callableMethods() >> callableMethods
        callableMethods.producing(target) >> Stream.of(candidate)
        method.parameters >> [first, second]
        first.getAnnotation(Ambient) >> null
        second.getAnnotation(Ambient) >> null

        expect:
        new MethodCallBridge().expand(Demands.forTarget(target), ctx).toList().empty
    }

    def 'accepts a candidate with one ambient parameter alongside the single mapped parameter'() {
        CallableMethods callableMethods = Mock()
        ExecutableElement method = Mock()
        VariableElement taxFactor = Mock()
        VariableElement order = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        ctx.callableMethods() >> callableMethods
        callableMethods.producing(target) >> Stream.of(candidate)
        method.parameters >> [taxFactor, order]
        method.returnType >> target
        method.simpleName >> nameOf('mapPrice')
        taxFactor.simpleName >> nameOf('taxFactor')
        taxFactor.asType() >> Mock(TypeMirror)
        order.simpleName >> nameOf('order')
        order.asType() >> Mock(TypeMirror)
        taxFactor.getAnnotation(Ambient) >> null
        order.getAnnotation(Ambient) >> ambient()
        ctx.isAssignable(target, target) >> true
        ctx.isSameType(target, target) >> true

        expect:
        new MethodCallBridge().expand(Demands.forTarget(target), ctx).toList().size() == 1
    }

    def 'rejects a candidate whose return type is not assignable to the demanded target'() {
        CallableMethods callableMethods = Mock()
        ExecutableElement method = Mock()
        VariableElement param = Mock()
        TypeMirror returnType = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        ctx.callableMethods() >> callableMethods
        callableMethods.producing(target) >> Stream.of(candidate)
        method.parameters >> [param]
        method.returnType >> returnType
        param.getAnnotation(Ambient) >> null
        ctx.isAssignable(returnType, target) >> false

        expect:
        new MethodCallBridge().expand(Demands.forTarget(target), ctx).toList().empty
    }

    def 'buildSpec weighs by METHOD plus the subtype distance and names the port after the parameter'() {
        ExecutableElement method = Mock()
        VariableElement param = Mock()
        TypeMirror paramType = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        method.parameters >> [param]
        method.returnType >> target
        method.simpleName >> nameOf('concat')
        param.simpleName >> nameOf('arg')
        param.asType() >> paramType
        param.getAnnotation(Ambient) >> null
        ctx.isSameType(target, target) >> true

        expect:
        def spec = new MethodCallBridge().buildSpec(candidate, target, Demands.forTarget(target), ctx)
        spec.weight == Weights.METHOD
        spec.ports[0].name == 'arg'
        spec.ports[0].type.is(paramType)
        spec.outputType.is(target)
        spec.label == 'concat(…)'

        and: 'the call is bound to the method as its call target (self-call guard rewiring)'
        spec.callTarget.get().is(method)
    }

    def 'buildSpec emits ports in declaration order, a BY_NAME/REQUIRE port carrying its binding name beside the mapped one'() {
        ExecutableElement method = Mock()
        VariableElement taxFactor = Mock()
        VariableElement order = Mock()
        TypeMirror taxFactorType = Mock()
        TypeMirror orderType = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        method.parameters >> [taxFactor, order]
        method.returnType >> target
        method.simpleName >> nameOf('mapPrice')
        taxFactor.simpleName >> nameOf('taxFactor')
        taxFactor.asType() >> taxFactorType
        order.simpleName >> nameOf('order')
        order.asType() >> orderType
        taxFactor.getAnnotation(Ambient) >> null
        order.getAnnotation(Ambient) >> ambient()
        ctx.isSameType(target, target) >> true

        expect:
        def spec = new MethodCallBridge().buildSpec(candidate, target, Demands.forTarget(target), ctx)
        spec.ports.size() == 2
        spec.ports[0].name == 'taxFactor'
        spec.ports[0].selector == Port.Selector.BY_TYPE
        spec.ports[0].onMiss == Port.OnMiss.MINT
        spec.ports[0].bindingName == ''
        spec.ports[1].name == 'order'
        spec.ports[1].selector == Port.Selector.BY_NAME
        spec.ports[1].onMiss == Port.OnMiss.REQUIRE
        spec.ports[1].bindingName == 'order'
    }

    def 'renderCodegen renders receiver.method(arg) chained via the zero-width wrap marker'() {
        ExecutableElement method = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        method.simpleName >> nameOf('concat')
        receiver.asExpression() >> CodeBlock.of('obj')
        def port = new Port('arg', Mock(TypeMirror), Nullability.NON_NULL)

        expect:
        def rendered = CodeBlock.of('$L\n',
                new MethodCallBridge().renderCodegen(candidate, [port]).render(byNameInput(arg: CodeBlock.of('$N', 'x'))))
        rendered.toString().contains('obj.concat(x)')
    }

    def 'renderCodegen renders multiple arguments positionally in declaration order — mapped then ambient'() {
        ExecutableElement method = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        method.simpleName >> nameOf('mapPrice')
        receiver.asExpression() >> CodeBlock.of('obj')
        def taxFactor = new Port('taxFactor', Mock(TypeMirror), Nullability.NON_NULL)
        def order = Port.byName('order', Mock(TypeMirror), Nullability.NON_NULL, 'order')

        expect:
        def rendered = CodeBlock.of('$L\n', new MethodCallBridge().renderCodegen(candidate, [taxFactor, order])
                .render(byNameInput(taxFactor: CodeBlock.of('$N', 'tf'), order: CodeBlock.of('$N', 'ord'))))
        rendered.toString().contains('mapPrice(tf, ord)')
    }

    def 'renderCodegen renders an ambient-first signature in declaration order too'() {
        ExecutableElement method = Mock()
        Receiver receiver = Mock()
        def candidate = new MethodCandidate(method, receiver)
        method.simpleName >> nameOf('mapPrice')
        receiver.asExpression() >> CodeBlock.of('obj')
        def order = Port.byName('order', Mock(TypeMirror), Nullability.NON_NULL, 'order')
        def taxFactor = new Port('taxFactor', Mock(TypeMirror), Nullability.NON_NULL)

        expect:
        def rendered = CodeBlock.of('$L\n', new MethodCallBridge().renderCodegen(candidate, [order, taxFactor])
                .render(byNameInput(order: CodeBlock.of('$N', 'ord'), taxFactor: CodeBlock.of('$N', 'tf'))))
        rendered.toString().contains('mapPrice(ord, tf)')
    }

    def 'ambientKey is empty for a parameter carrying no @Ambient'() {
        VariableElement param = Mock()
        param.getAnnotation(Ambient) >> null

        expect:
        new MethodCallBridge().ambientKey(param).empty
    }

    def 'ambientKey falls back to the parameter name when @Ambient declares no value'() {
        VariableElement param = Mock()
        param.getAnnotation(Ambient) >> ambient()
        param.simpleName >> nameOf('order')

        expect:
        new MethodCallBridge().ambientKey(param).get() == 'order'
    }

    def 'ambientKey prefers the @Ambient value over the parameter name'() {
        VariableElement param = Mock()
        param.getAnnotation(Ambient) >> ambient('tenant')

        expect:
        new MethodCallBridge().ambientKey(param).get() == 'tenant'
    }

    private static IncomingValues byNameInput(final Map<String, CodeBlock> values) {
        [byName: { String slotName -> values[slotName] }] as IncomingValues
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private static Ambient ambient(final String value = '') {
        [value: { -> value }] as Ambient
    }
}
