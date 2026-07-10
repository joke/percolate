package io.github.joke.percolate.processor.internal.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link SelfCallGuard} seam, unit-tested directly: a method may not be landed calling a same-shaped abstract
 * method's whole parameter ({@code this.m(param)}) — a degenerate infinite recursion the cost model cannot reject,
 * whether the call target <em>is</em> the enclosing method (true self-call) or a <em>different</em> abstract method
 * sharing its (parameter types, return type) shape (a mutual 2-cycle: two same-shaped mapper methods each picking
 * the other as their cheapest producer, e.g. two methods differing only by a {@code @Nullable} on the return, which
 * is invisible to {@link ResolveCtx#isSameType}). The guard compares the spec's neutral call-target shape to the
 * enclosing method's via the {@link ResolveCtx} type-query seam (not raw {@code TypeMirror#toString}, which would
 * let a type-use annotation leak into the comparison) and checks whether a bound port is that method's own
 * parameter-root {@code LEAF}; each case isolates one branch of {@code refuses}.
 *
 * <p>Unit-tested mock-only: {@link FakeElements} stand in for the compiled {@code ExecutableElement}s (the guard
 * only ever reads their modifiers/parameters/return type, never a raw {@code Types}/{@code Elements} lookup); a
 * mocked {@link ResolveCtx} answers every {@code isSameType} question by identity; every {@link TypeMirror} is a
 * plain opaque {@code Mock} — the guard carries them through only to compare via the seam. No javac.
 */
@Tag('unit')
class SelfCallGuardSpec extends Specification {

    @Shared TypeMirror personType = Mock()
    @Shared TypeMirror humanType = Mock()
    @Shared TypeMirror stringType = Mock()
    @Shared OperationCodegen codegen = { inc -> CodeBlock.of('x') } as OperationCodegen

    ResolveCtx ctx = Mock()
    SelfCallGuard guard = new SelfCallGuard(ctx)

    def setup() {
        ctx.isSameType(_ as TypeMirror, _ as TypeMirror) >> { TypeMirror a, TypeMirror b -> a.is(b) }
    }

    def 'refuses a same-shape call target bound to its own whole-parameter root'() {
        def method = mapMethod()
        def scope = new MethodScope(method)

        expect:
        guard.refuses(scope, call('map', method), [bind(scope, paramRoot(method))])
    }

    def 'refuses a different, same-shaped abstract method bound to the enclosing method whole-parameter root'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def sibling = FakeElements.method('mapAlias', humanType, FakeElements.param('other', personType))

        expect: 'mapAlias(Person):Human is a different method but the identical shape as map(Person):Human'
        guard.refuses(scope, call('mapAlias', sibling), [bind(scope, paramRoot(method))])
    }

    def 'does not refuse delegation to a same-shaped default method (a fixed, developer-written body)'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def helper = FakeElements.defaultMethod('normalize', humanType, FakeElements.param('other', personType))

        expect: 'a default method can never be re-entrant into this run, whatever its shape'
        !guard.refuses(scope, call('normalize', helper), [bind(scope, paramRoot(method))])
    }

    def 'does not refuse when the scope is not a method scope (a child/element scope cannot self-recur)'() {
        def method = mapMethod()

        expect: 'the self-call shape is identical, but a non-MethodScope short-circuits the guard'
        !guard.refuses(new HarnessScope('element'), call('map', method),
                [bind(new MethodScope(method), paramRoot(method))])
    }

    def 'does not refuse a producer that records no call target'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def producer = OperationSpec.of('new', codegen, Weights.STEP,
                [Port.reuse('arg', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        expect:
        !guard.refuses(scope, producer, [bind(scope, paramRoot(method))])
    }

    def 'does not refuse delegation to a different-shaped method'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def getFirstName = FakeElements.method('getFirstName', stringType)

        expect: 'getFirstName() and map(Person):Human differ in shape — not a self-call'
        !guard.refuses(scope, call('getFirstName', getFirstName), [bind(scope, paramRoot(method))])
    }

    def 'does not refuse a self-call bound to a sub-part of the parameter (an ACCESS source), not the whole parameter'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def subPart = new SourceLocation(new AccessPath(['person', 'firstName']))

        expect:
        !guard.refuses(scope, call('map', method), [bind(scope, subPart)])
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static SourceLocation paramRoot(final ExecutableElement method) {
        new SourceLocation(AccessPath.of(method.parameters[0].simpleName.toString()))
    }

    private ExecutableElement mapMethod() {
        FakeElements.method('map', humanType, FakeElements.param('person', personType))
    }

    private OperationSpec call(final String label, final ExecutableElement target) {
        OperationSpec.callOf(label, codegen, Weights.METHOD,
                [Port.reuse('arg', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL, target)
    }

    private PortBinding bind(final Scope scope, final SourceLocation location) {
        new PortBinding(Port.reuse('arg', personType, Nullability.NON_NULL),
                new AddValue(scope, location, personType, Nullability.NON_NULL))
    }
}
