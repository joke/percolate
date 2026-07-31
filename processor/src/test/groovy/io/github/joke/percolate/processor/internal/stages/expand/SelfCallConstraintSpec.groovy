package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.spi.BoundPort
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link SelfCallConstraint} unit-tested mock-only: the engine's own landing constraint (design D8 of change
 * {@code decouple-engine-from-strategy-semantics}) refusing a call that would land the method being generated on its
 * own whole parameter. Method shape is read through coerced {@link ExecutableElement} stand-ins and the
 * {@link ResolveCtx} type-query seam; binding identity is compared through the opaque {@link BoundPort} value
 * identity, never the graph.
 */
@Tag('unit')
class SelfCallConstraintSpec extends Specification {

    ResolveCtx ctx = Mock()
    Codegen codegen = Mock()
    TypeMirror returnType = Mock()
    TypeMirror paramType = Mock()

    def 'refuses a same-shaped abstract self-call bound to the enclosing method\'s own whole parameter'() {
        def method = abstractMethod('dto')
        def constraint = new SelfCallConstraint(ctx, new MethodScope(method))
        def candidate = callSpec('map(…)', method)

        when:
        def refusal = constraint.check(candidate, [boundTo(parameterRoot('dto'))])

        then:
        1 * ctx.isSameType(returnType, returnType) >> true
        1 * ctx.isSameType(paramType, paramType) >> true
        0 * _

        expect:
        refusal.get().message == "landing 'map(…)' here would call the method being generated on its own whole" +
                ' parameter — a degenerate self-call'
    }

    def 'admits a same-shaped abstract call bound to a sub-part of the parameter rather than its root'() {
        def method = abstractMethod('dto')
        def constraint = new SelfCallConstraint(ctx, new MethodScope(method))

        when:
        def refusal = constraint.check(callSpec('map(…)', method), [boundTo(new SourceLocation(new AccessPath(['dto', 'street'])))])

        then:
        1 * ctx.isSameType(returnType, returnType) >> true
        1 * ctx.isSameType(paramType, paramType) >> true
        0 * _

        expect:
        refusal.empty
    }

    def 'admits a candidate carrying no call target at all'() {
        def constraint = new SelfCallConstraint(ctx, new MethodScope(abstractMethod('dto')))
        def candidate = OperationSpec.of('copy', codegen, 1, [], returnType, Nullability.NON_NULL)

        expect:
        constraint.check(candidate, [boundTo(parameterRoot('dto'))]).empty
    }

    def 'admits a call target that is not abstract — a default method is real delegation, not recursion'() {
        def method = abstractMethod('dto')
        def constraint = new SelfCallConstraint(ctx, new MethodScope(method))
        def defaultMethod = methodOf([Modifier.DEFAULT], ['dto'])

        expect:
        constraint.check(callSpec('helper(…)', defaultMethod), [boundTo(parameterRoot('dto'))]).empty
    }

    def 'admits any candidate at all when the enclosing scope is not a method scope'() {
        Scope childScope = Mock()
        def constraint = new SelfCallConstraint(ctx, childScope)
        def method = abstractMethod('dto')

        expect:
        constraint.check(callSpec('map(…)', method), [boundTo(parameterRoot('dto'))]).empty
    }

    def 'isSameShapedAbstractSelfCallTarget holds for an abstract, same-shaped call target'() {
        def method = abstractMethod('dto')
        def constraint = new SelfCallConstraint(ctx, new MethodScope(method))
        ctx.isSameType(returnType, returnType) >> true
        ctx.isSameType(paramType, paramType) >> true

        expect:
        constraint.isSameShapedAbstractSelfCallTarget(callSpec('map(…)', method))
    }

    def 'isSameShapedAbstractSelfCallTarget is false for an abstract call target of a different shape'() {
        def method = abstractMethod('dto')
        def constraint = new SelfCallConstraint(ctx, new MethodScope(method))
        def other = methodOf([Modifier.ABSTRACT], ['dto', 'locale'])

        expect:
        !constraint.isSameShapedAbstractSelfCallTarget(callSpec('map(…)', other))
    }

    def 'parameterRootLocations is one LEAF source location per parameter, keyed by its own name'() {
        def constraint = new SelfCallConstraint(ctx, new MethodScope(abstractMethod('dto')))

        expect:
        constraint.parameterRootLocations(methodOf([Modifier.ABSTRACT], ['dto', 'locale']))
                == [parameterRoot('dto'), parameterRoot('locale')] as Set
    }

    def 'parameterRootLocations is empty for a method taking no parameters'() {
        def constraint = new SelfCallConstraint(ctx, new MethodScope(abstractMethod('dto')))

        expect:
        constraint.parameterRootLocations(methodOf([Modifier.ABSTRACT], [])).empty
    }

    def 'sameShape is false when the parameter counts differ, without asking the seam anything'() {
        def constraint = new SelfCallConstraint(ctx, new MethodScope(abstractMethod('dto')))

        when:
        def same = constraint.sameShape(methodOf([Modifier.ABSTRACT], ['dto']), methodOf([Modifier.ABSTRACT], []))

        then:
        0 * _

        expect:
        !same
    }

    def 'sameShape is false when the return types differ, without comparing any parameter'() {
        def constraint = new SelfCallConstraint(ctx, new MethodScope(abstractMethod('dto')))

        when:
        def same = constraint.sameShape(methodOf([Modifier.ABSTRACT], ['dto']), methodOf([Modifier.ABSTRACT], ['other']))

        then:
        1 * ctx.isSameType(returnType, returnType) >> false
        0 * _

        expect:
        !same
    }

    def 'sameShape is false when a parameter type differs positionally'() {
        def constraint = new SelfCallConstraint(ctx, new MethodScope(abstractMethod('dto')))
        TypeMirror otherParamType = Mock()
        def other = element([getModifiers: { -> [Modifier.ABSTRACT] as Set },
                             getReturnType: { -> returnType },
                             getParameters: { -> [parameter('other', otherParamType)] }])

        when:
        def same = constraint.sameShape(methodOf([Modifier.ABSTRACT], ['dto']), other)

        then:
        1 * ctx.isSameType(returnType, returnType) >> true
        1 * ctx.isSameType(paramType, otherParamType) >> false
        0 * _

        expect:
        !same
    }

    def 'sameShape holds for equal parameter types and return type, whatever the names'() {
        def constraint = new SelfCallConstraint(ctx, new MethodScope(abstractMethod('dto')))

        when:
        def same = constraint.sameShape(methodOf([Modifier.ABSTRACT], ['dto']), methodOf([Modifier.ABSTRACT], ['other']))

        then:
        1 * ctx.isSameType(returnType, returnType) >> true
        1 * ctx.isSameType(paramType, paramType) >> true
        0 * _

        expect:
        same
    }

    private static Location parameterRoot(final String name) {
        new SourceLocation(AccessPath.of(name))
    }

    private static ExecutableElement element(final Map<String, Closure> members) {
        members as ExecutableElement
    }

    private static VariableElement parameter(final String name, final TypeMirror type) {
        [getSimpleName: { -> nameOf(name) }, asType: { -> type }] as VariableElement
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private OperationSpec callSpec(final String label, final ExecutableElement callTarget) {
        OperationSpec.callOf(label, codegen, 1, [], returnType, Nullability.NON_NULL, callTarget)
    }

    private BoundPort boundTo(final Location location) {
        new BoundPort(
                Port.byTypeOrDecline('value', paramType, Nullability.NON_NULL),
                new AddValue(Mock(Scope), location, paramType, Nullability.NON_NULL))
    }

    private ExecutableElement abstractMethod(final String parameterName) {
        methodOf([Modifier.ABSTRACT], [parameterName])
    }

    // Coerced stand-ins rather than mocks: the constraint reads only these four members, and a mock's every read
    // would count against the strict interaction budget the seam questions are pinned with.
    private ExecutableElement methodOf(final List<Modifier> modifiers, final List<String> parameterNames) {
        element([getModifiers: { -> modifiers as Set },
                 getReturnType: { -> returnType },
                 getParameters: { -> parameterNames.collect { parameter(it, paramType) } }])
    }
}
